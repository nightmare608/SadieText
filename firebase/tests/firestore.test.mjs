import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {before, after, beforeEach, test} from 'node:test';
import {readFile} from 'node:fs/promises';
import {initializeTestEnvironment, assertFails, assertSucceeds} from '@firebase/rules-unit-testing';
import {doc, getDoc, getDocs, collection, query, where, limit, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp, runTransaction, orderBy, startAfter, documentId, getDocFromServer, collectionGroup, onSnapshot} from 'firebase/firestore';
let env;
const legacyId=(a,b)=>createHash('sha256').update(JSON.stringify([a,b].sort())).digest('hex');
const pairId=(a,b)=>'p2:'+ [a,b].sort().join(':');
const ab=pairId('alice','bob');
const chatPath=id=>'chats/'+id;
const bundle={signedId:1,kyberId:1,identity:'a'.repeat(44),registration:42,signedKey:'b'.repeat(44),signedSignature:'c'.repeat(88),kyberKey:'d'.repeat(2092),kyberSignature:'e'.repeat(88)};
const profile=u=>({name:u,username:u+'123',bundle});
const chat={members:['alice','bob'],names:{alice:'alice123',bob:'bob123'},updatedAt:Timestamp.now()};
const message={sender:'alice',recipient:'bob',senderIdentity:bundle.identity,recipientIdentity:bundle.identity,type:3,ciphertext:'opaque-ciphertext'.repeat(5),createdAt:Timestamp.now(),readAt:null};
const db=(uid,verified=true)=>env.authenticatedContext(uid,{email_verified:verified,auth_time:Math.floor(Date.now()/1000)}).firestore();
const msg=(d,id='m1')=>doc(d,'chats/'+ab+'/messages/'+id);
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-sakin',firestore:{rules:await readFile('../firestore.rules','utf8'),host:'127.0.0.1',port:8080}})});
after(async()=>{await env?.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  const d=c.firestore();
  for(const u of ['alice','bob','eve']) {await setDoc(doc(d,'users/'+u),profile(u));await setDoc(doc(d,'handles/'+u+'123'),{uid:u})}
  await setDoc(doc(d,chatPath(ab)),chat);await setDoc(msg(d),message);
 });
});
test('alice and bob can read their message',async()=>{await assertSucceeds(getDoc(msg(db('alice'))));await assertSucceeds(getDoc(msg(db('bob'))))});
test('third person cannot read message or chat',async()=>{await assertFails(getDoc(msg(db('eve'))));await assertFails(getDoc(doc(db('eve'),chatPath(ab))))});
test('signed out cannot read',async()=>{await assertFails(getDoc(msg(env.unauthenticatedContext().firestore())))});
test('unverified user cannot read or search',async()=>{await assertFails(getDoc(msg(db('alice',false))));await assertFails(getDoc(doc(db('alice',false),'handles/bob123')))});
test('users cannot enumerate profiles or usernames',async()=>{await assertFails(getDocs(collection(db('alice'),'users')));await assertFails(getDocs(collection(db('alice'),'handles')))});
test('exact username lookup is permitted',async()=>{await assertSucceeds(getDoc(doc(db('alice'),'handles/bob123')))});
test('chat membership cannot be replaced',async()=>{await assertFails(updateDoc(doc(db('alice'),chatPath(ab)),{members:['alice','eve']}))});
test('a third person cannot inject messages',async()=>{await assertFails(setDoc(msg(db('eve'),'evil'),{...message,sender:'eve',createdAt:serverTimestamp()}))});
test('sender identity cannot be spoofed',async()=>{await assertFails(setDoc(msg(db('alice'),'evil'),{...message,sender:'bob',recipient:'alice',createdAt:serverTimestamp()}))});
test('valid ciphertext-only message can be created',async()=>{await assertSucceeds(setDoc(msg(db('alice'),'m2'),{...message,createdAt:serverTimestamp()}))});
test('plaintext field is rejected',async()=>{await assertFails(setDoc(msg(db('alice'),'m2'),{...message,createdAt:serverTimestamp(),text:'secret'}))});
test('client timestamp and oversized ciphertext are rejected',async()=>{await assertFails(setDoc(msg(db('alice'),'m2'),message));await assertFails(setDoc(msg(db('alice'),'m3'),{...message,createdAt:serverTimestamp(),ciphertext:'a'.repeat(20001)}))});
test('recipient alone can set read receipt once',async()=>{await assertFails(updateDoc(msg(db('alice')),{readAt:serverTimestamp()}));await assertSucceeds(updateDoc(msg(db('bob')),{readAt:serverTimestamp()}));await assertFails(updateDoc(msg(db('bob')),{readAt:null}))});
test('recipient cannot modify ciphertext',async()=>{await assertFails(updateDoc(msg(db('bob')),{ciphertext:'tampered'}))});
test('only sender can delete server ciphertext',async()=>{await assertFails(deleteDoc(msg(db('bob'))));await assertFails(deleteDoc(msg(db('eve'))));await assertSucceeds(deleteDoc(msg(db('alice'))))});
test('scoped limited chat and message queries work',async()=>{const d=db('alice');await assertSucceeds(getDocs(query(collection(d,'chats'),where('members','array-contains','alice'),limit(50))));await assertSucceeds(getDocs(query(collection(d,'chats/'+ab+'/messages'),limit(50))))});
test('unbounded and unrelated list queries fail',async()=>{const d=db('alice');await assertFails(getDocs(collection(d,'chats')));await assertFails(getDocs(collection(d,'chats/'+ab+'/messages')));await assertFails(getDocs(query(collection(db('eve'),'chats/'+ab+'/messages'),limit(50))))});
test('profile and username must be reserved atomically',async()=>{const d=db('newuser');await assertFails(setDoc(doc(d,'users/newuser'),profile('newuser')));const b=writeBatch(d);b.set(doc(d,'users/newuser'),profile('newuser'));b.set(doc(d,'handles/newuser123'),{uid:'newuser'});await assertSucceeds(b.commit())});
test('a reserved username cannot be stolen',async()=>{await assertFails(setDoc(doc(db('eve'),'handles/alice123'),{uid:'eve'}))});
test('only owner can rotate public identity',async()=>{await assertFails(updateDoc(doc(db('eve'),'users/alice'),{bundle}));await assertSucceeds(updateDoc(doc(db('alice'),'users/alice'),{bundle:{...bundle,registration:43}}))});
test('profile deletion revokes access even with an old token',async()=>{await env.withSecurityRulesDisabled(c=>deleteDoc(doc(c.firestore(),'users/alice')));await assertFails(getDoc(msg(db('alice'))))});
test('profile and handle deletion must be atomic',async()=>{const d=db('alice');await assertFails(deleteDoc(doc(d,'users/alice')));const b=writeBatch(d);b.delete(doc(d,'handles/alice123'));b.delete(doc(d,'users/alice'));b.set(doc(d,'deletedAccounts/alice'),{deletedAt:serverTimestamp()});await assertSucceeds(b.commit())});
test('new two-person chat requires both real members and accurate handles',async()=>{const d=db('alice');await assertSucceeds(setDoc(doc(d,chatPath(pairId('alice','eve'))),{members:['alice','eve'],names:{alice:'alice123',eve:'eve123'},updatedAt:serverTimestamp()}));await assertFails(setDoc(doc(d,chatPath(pairId('alice','missing'))),{members:['alice','missing'],names:{alice:'alice123',missing:'x'},updatedAt:serverTimestamp()}))});

test('a deleted account cannot recreate its profile using an old token',async()=>{
 const d=db('alice');const b=writeBatch(d);b.delete(doc(d,'handles/alice123'));b.delete(doc(d,'users/alice'));b.set(doc(d,'deletedAccounts/alice'),{deletedAt:serverTimestamp()});await assertSucceeds(b.commit());
 const restore=writeBatch(d);restore.set(doc(d,'users/alice'),profile('alice'));restore.set(doc(d,'handles/alice123'),{uid:'alice'});await assertFails(restore.commit());
 await assertFails(deleteDoc(doc(d,'deletedAccounts/alice')));
 await assertFails(setDoc(doc(db('eve'),'deletedAccounts/alice'),{deletedAt:serverTimestamp()}));
});

test('messages addressed to revoked device identities are rejected',async()=>{const d=db('alice');await assertFails(setDoc(msg(d,'stale-sender'),{...message,senderIdentity:'z'.repeat(44),createdAt:serverTimestamp()}));await assertFails(setDoc(msg(d,'stale-recipient'),{...message,recipientIdentity:'z'.repeat(44),createdAt:serverTimestamp()}))});

test('identity replacement requires a recent login',async()=>{const stale=env.authenticatedContext('alice',{email_verified:true,auth_time:0}).firestore();await assertFails(updateDoc(doc(stale,'users/alice'),{bundle:{...bundle,identity:'z'.repeat(44)}}));await assertSucceeds(updateDoc(doc(db('alice'),'users/alice'),{bundle:{...bundle,identity:'z'.repeat(44)}}))});

test('deletion marker cannot be created without deleting the profile',async()=>{await assertFails(setDoc(doc(db('alice'),'deletedAccounts/alice'),{deletedAt:serverTimestamp()}))});

test('both participants can send as themselves and advance chat timestamp atomically',async()=>{
 const d=db('bob');const b=writeBatch(d);
 b.set(msg(d,'bob-reply'),{...message,sender:'bob',recipient:'alice',createdAt:serverTimestamp()});
 b.update(doc(d,chatPath(ab)),{updatedAt:serverTimestamp()});await assertSucceeds(b.commit());
});
test('third person cannot change receipts, chat metadata or delete messages',async()=>{
 const d=db('eve');await assertFails(updateDoc(msg(d),{readAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(d,chatPath(ab)),{updatedAt:serverTimestamp()}));await assertFails(deleteDoc(msg(d)));
});
test('group chats and self chats are rejected',async()=>{
 const d=db('alice');await assertFails(setDoc(doc(d,'chats/group'),{members:['alice','bob','eve'],names:{alice:'alice123',bob:'bob123',eve:'eve123'},updatedAt:serverTimestamp()}));
 await assertFails(setDoc(doc(d,'chats/self'),{members:['alice','alice'],names:{alice:'alice123'},updatedAt:serverTimestamp()}));
});
test('verified users can fetch a public profile needed for key exchange',async()=>{await assertSucceeds(getDoc(doc(db('alice'),'users/bob')))});
test('private fields cannot be added to public profiles or public bundles',async()=>{
 for(const field of ['email','password','privateKey']){
  const d=db('newuser');const b=writeBatch(d);b.set(doc(d,'users/newuser'),{...profile('newuser'),[field]:'private'});b.set(doc(d,'handles/newuser123'),{uid:'newuser'});await assertFails(b.commit());
  await assertFails(updateDoc(doc(db('alice'),'users/alice'),{[field]:'private'}));
 }
 await assertFails(updateDoc(doc(db('alice'),'users/alice'),{bundle:{...bundle,privateKey:'private'}}));
});
test('another user cannot modify or delete a profile',async()=>{
 const d=db('eve');await assertFails(updateDoc(doc(d,'users/alice'),{name:'changed'}));await assertFails(deleteDoc(doc(d,'users/alice')));
});
test('private subcollections and unknown paths deny reads and writes even to owner',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'users/alice/private/account'),{email:'private@example.invalid'});
  await setDoc(doc(c.firestore(),'privateUsers/alice'),{privateKey:'private'});
 });
 for(const uid of ['alice','eve'])for(const path of ['users/alice/private/account','privateUsers/alice']){
  await assertFails(getDoc(doc(db(uid),path)));await assertFails(setDoc(doc(db(uid),path),{value:'changed'}));
 }
});
test('account deletion marker is visible only to its owner',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'deletedAccounts/alice'),{deletedAt:Timestamp.now()}));
 await assertSucceeds(getDoc(doc(db('alice'),'deletedAccounts/alice')));
 await assertFails(getDoc(doc(db('eve'),'deletedAccounts/alice')));
 await assertFails(getDocs(collection(db('alice'),'deletedAccounts')));
 await assertFails(getDoc(msg(db('alice'))));
});
test('deletion marker blocks new chats and deliveries to a revoked account',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'deletedAccounts/bob'),{deletedAt:Timestamp.now()}));
 const d=db('alice');await assertFails(setDoc(doc(d,'chats/new-ab'),{...chat,updatedAt:serverTimestamp()}));
 await assertFails(setDoc(msg(d,'revoked'),{...message,createdAt:serverTimestamp()}));
});
test('messages cannot target a person outside the chat or change their sender',async()=>{
 const d=db('alice');await assertFails(setDoc(msg(d,'outside'),{...message,recipient:'eve',createdAt:serverTimestamp()}));
 await assertFails(updateDoc(msg(d),{sender:'bob',recipient:'alice'}));
});
test('numeric protocol type must be a supported integer',async()=>{
 for(const type of [1,4,2.5,'3'])await assertFails(setDoc(msg(db('alice'),'bad-type'),{...message,type,createdAt:serverTimestamp()}));
});
test('message query limits preserve cleanup but reject oversized pages',async()=>{
 const d=db('alice');await assertSucceeds(getDocs(query(collection(d,'chats/'+ab+'/messages'),where('sender','==','alice'),limit(100))));
 await assertFails(getDocs(query(collection(d,'chats/'+ab+'/messages'),limit(101))));
 await assertFails(getDocs(query(collection(d,'chats'),where('members','array-contains','alice'),limit(51))));
});
test('absent chats deny get while participants can read absent messages',async()=>{
 const d=db('alice');await assertFails(getDoc(doc(d,'chats/not-created')));await assertSucceeds(getDoc(msg(d,'not-created')));
 await assertFails(getDoc(msg(db('eve'),'not-created')));
});
test('a sender cannot overwrite an existing message with the same ID',async()=>{
 await assertFails(setDoc(msg(db('alice')),{...message,ciphertext:'replacement'.repeat(5),createdAt:serverTimestamp()}));
});

test('AUDIT: absent foreign chat must deny just like an existing foreign chat',async()=>{
 const d=db('eve');await assertFails(getDoc(doc(d,chatPath(ab))));await assertFails(getDoc(doc(d,'chats/never-existed')));
});
test('AUDIT: a guessed foreign chat ID cannot be probed with an own-member write',async()=>{
 const d=db('eve');const payload={members:['alice','eve'],names:{alice:'alice123',eve:'eve123'},updatedAt:serverTimestamp()};
 await assertFails(setDoc(doc(d,chatPath(ab)),payload));await assertFails(setDoc(doc(d,'chats/guessed-absent-id'),payload));
});

async function denied(operation) {
 await assert.rejects(operation,error=>{assert.equal(error.code,'permission-denied');return true;});
}
const chatPayload=(a,b)=>({members:[a,b].sort(),names:{[a]:a+'123',[b]:b+'123'},updatedAt:serverTimestamp()});
async function claim(d,uid,handle) {
 return runTransaction(d,async tx=>{
  const h=doc(d,'handles/'+handle),u=doc(d,'users/'+uid);
  const existing=await tx.get(h);const self=await tx.get(u);
  if(existing.exists()||self.exists())throw Error('Already reserved');
  tx.set(h,{uid});tx.set(u,{...profile(uid),username:handle});
 });
}
test('AUDIT: foreign existing and absent chats return permission-denied for every mutation and get',async()=>{
 const d=db('eve');
 for(const id of [ab,pairId('alice','missing'),'random-guess']){
  const ref=doc(d,chatPath(id));await denied(getDocFromServer(ref));
  await denied(setDoc(ref,chatPayload('alice','eve')));
  await denied(updateDoc(ref,{updatedAt:serverTimestamp()}));await denied(deleteDoc(ref));
 }
});
test('AUDIT: foreign and absent parent messages return identical authorization errors',async()=>{
 const d=db('eve');
 for(const id of [ab,pairId('alice','missing')])for(const mid of ['m1','absent-message']){
  const ref=doc(d,chatPath(id)+'/messages/'+mid);
  await denied(getDocFromServer(ref));await denied(updateDoc(ref,{readAt:serverTimestamp()}));await denied(deleteDoc(ref));
  await denied(setDoc(ref,{...message,sender:'eve',recipient:'alice',createdAt:serverTimestamp()}));
 }
});
test('AUDIT: blind open creates a new chat and either participant can reopen it',async()=>{
 const id=pairId('alice','eve');const a=db('alice'),e=db('eve');
 await denied(getDocFromServer(doc(a,chatPath(id))));
 await assertSucceeds(setDoc(doc(a,chatPath(id)),chatPayload('alice','eve')));
 await assertSucceeds(setDoc(doc(e,chatPath(id)),chatPayload('eve','alice')));
 const snap=await assertSucceeds(getDocFromServer(doc(e,chatPath(id))));assert.deepEqual(snap.data().members,['alice','eve']);
});
test('AUDIT: simultaneous first opens converge on one chat',async()=>{
 const id=pairId('alice','eve');await Promise.all([
  setDoc(doc(db('alice'),chatPath(id)),chatPayload('alice','eve')),
  setDoc(doc(db('eve'),chatPath(id)),chatPayload('eve','alice'))
 ]);await assertSucceeds(getDocFromServer(doc(db('alice'),chatPath(id))));
});
test('AUDIT: chat IDs are bound to the sorted pair, not arbitrary user input',async()=>{
 const d=db('alice');const id=pairId('alice','eve');
 for(const bad of ['arbitrary',id.toUpperCase(),pairId('bob','eve'),createHash('sha256').update(JSON.stringify(['eve','alice'])).digest('hex')])
  await denied(setDoc(doc(d,chatPath(bad)),chatPayload('alice','eve')));
 await denied(setDoc(doc(d,chatPath(id)),{...chatPayload('alice','eve'),members:['eve','alice']}));
 await assertSucceeds(setDoc(doc(d,chatPath(id)),chatPayload('alice','eve')));
});
test('AUDIT: nonexistent chat get remains forbidden even to would-be participants',async()=>{
 for(const uid of ['alice','eve'])await denied(getDocFromServer(doc(db(uid),chatPath(pairId('alice','eve')))));
});
test('AUDIT: actual Android claim transaction reserves the reciprocal profile and handle',async()=>{
 await assertSucceeds(claim(db('newuser'),'newuser','fresh_handle'));
 assert.equal((await getDoc(doc(db('alice'),'handles/fresh_handle'))).data().uid,'newuser');
});
test('AUDIT: two concurrent username claims have exactly one winner',async()=>{
 const results=await Promise.allSettled([claim(db('newa'),'newa','shared_handle'),claim(db('newb'),'newb','shared_handle')]);
 assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 const winner=(await getDoc(doc(db('alice'),'handles/shared_handle'))).data().uid;
 assert.equal((await getDoc(doc(db(winner),'users/'+winner))).data().username,'shared_handle');
 const loser=winner==='newa'?'newb':'newa';assert.equal((await getDoc(doc(db(loser),'users/'+loser))).exists(),false);
});
test('AUDIT: handle-only, mismatched links, impersonation and additional aliases are rejected',async()=>{
 const d=db('newuser');await denied(setDoc(doc(d,'handles/newuser123'),{uid:'newuser'}));
 for(const target of ['eve','newuser']){
  const b=writeBatch(d);b.set(doc(d,'handles/wrong_handle'),{uid:target});b.set(doc(d,'users/newuser'),profile('newuser'));await denied(b.commit());
 }
 await denied(setDoc(doc(db('alice'),'handles/extra_alias'),{uid:'alice'}));
 await denied(deleteDoc(doc(db('eve'),'handles/alice123')));
});
test('AUDIT: a batch cannot create a profile and its deletion marker together',async()=>{
 const d=db('newuser');const b=writeBatch(d);
 b.set(doc(d,'users/newuser'),profile('newuser'));b.set(doc(d,'handles/newuser123'),{uid:'newuser'});b.set(doc(d,'deletedAccounts/newuser'),{deletedAt:serverTimestamp()});await denied(b.commit());
});
test('AUDIT: handle and profile deletion require complete reciprocal post-state',async()=>{
 const d=db('alice');await denied(deleteDoc(doc(d,'handles/alice123')));
 const b=writeBatch(d);b.delete(doc(d,'users/alice'));b.delete(doc(d,'handles/alice123'));await denied(b.commit());
 const c=writeBatch(d);c.delete(doc(d,'users/alice'));c.set(doc(d,'deletedAccounts/alice'),{deletedAt:serverTimestamp()});await denied(c.commit());
});
test('AUDIT: stale login cannot delete the profile-handle-marker triple',async()=>{
 const d=env.authenticatedContext('alice',{email_verified:true,auth_time:0}).firestore();const b=writeBatch(d);
 b.delete(doc(d,'users/alice'));b.delete(doc(d,'handles/alice123'));b.set(doc(d,'deletedAccounts/alice'),{deletedAt:serverTimestamp()});await denied(b.commit());
});
test('AUDIT: actual Android message transaction is idempotent and cannot overwrite ciphertext',async()=>{
 const d=db('alice');const ref=msg(d,'transaction-id');
 const deliver=()=>runTransaction(d,async tx=>{
  const old=await tx.get(ref);
  if(old.exists()){assert.equal(old.data().ciphertext,message.ciphertext);return;}
  tx.set(ref,{...message,createdAt:serverTimestamp()});tx.update(doc(d,chatPath(ab)),{updatedAt:serverTimestamp()});
 });await assertSucceeds(deliver());const first=(await getDoc(ref)).data().createdAt;
 await assertSucceeds(deliver());assert.equal((await getDoc(ref)).data().createdAt.isEqual(first),true);
});
test('AUDIT: exact Android sorted queries and pagination remain authorized',async()=>{
 const d=db('alice');const cq=()=>query(collection(d,'chats'),where('members','array-contains','alice'),orderBy('updatedAt','desc'),limit(50));
 const cs=await assertSucceeds(getDocs(cq()));assert.equal(cs.size,1);
 await assertSucceeds(getDocs(query(collection(d,'chats'),where('members','array-contains','alice'),orderBy('updatedAt','desc'),startAfter(cs.docs[0]),limit(50))));
 const mq=()=>query(collection(d,chatPath(ab)+'/messages'),orderBy('createdAt','desc'),limit(50));
 const ms=await assertSucceeds(getDocs(mq()));assert.equal(ms.size,1);
 await assertSucceeds(getDocs(query(collection(d,chatPath(ab)+'/messages'),orderBy('createdAt','desc'),startAfter(ms.docs[0]),limit(50))));
 await assertSucceeds(getDocs(query(collection(d,'chats'),where('members','array-contains','alice'),orderBy(documentId()),limit(30))));
});
test('AUDIT: limited global queries and other-member filters cannot enumerate chats',async()=>{
 const d=db('eve');await denied(getDocs(query(collection(d,'chats'),limit(50))));
 await denied(getDocs(query(collection(d,'chats'),where('members','array-contains','alice'),limit(50))));
 await denied(getDocs(query(collectionGroup(d,'messages'),limit(50))));
});
test('AUDIT: own-membership queries reveal nothing about guessed foreign or absent IDs',async()=>{
 const d=db('eve');for(const id of [ab,pairId('alice','missing')]){
  await denied(getDocs(query(collection(d,'chats'),where('members','array-contains','eve'),where(documentId(),'==',id),limit(1))));
 }
});
test('AUDIT: malformed legacy profiles with deletion markers cannot rotate keys',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'deletedAccounts/alice'),{deletedAt:Timestamp.now()}));
 await denied(updateDoc(doc(db('alice'),'users/alice'),{bundle:{...bundle,registration:43}}));
});
test('AUDIT: admin-added private fields in a public profile are not readable by others',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'users/alice'),{email:'private@example.invalid'}));
 await denied(getDocFromServer(doc(db('eve'),'users/alice')));
 await assertSucceeds(getDocFromServer(doc(db('alice'),'users/alice')));
});
test('AUDIT: new users can read their absent profile before atomic registration',async()=>{
 const result=await assertSucceeds(getDocFromServer(doc(db('newuser'),'users/newuser')));assert.equal(result.exists(),false);
 await denied(getDocFromServer(doc(env.unauthenticatedContext().firestore(),'users/alice')));
});
test('AUDIT: Android mixed-case UID vector matches delimiter-safe rules ID',async()=>{
 const id='p2:Alice_1:bob-2';
 assert.equal(pairId('bob-2','Alice_1'),id);
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'users/Alice_1'),{...profile('alice'),username:'alice_1'});
  await setDoc(doc(c.firestore(),'users/bob-2'),{...profile('bob'),username:'bob_2'});
 });
 await assertSucceeds(setDoc(doc(db('Alice_1'),chatPath(id)),{members:['Alice_1','bob-2'],names:{Alice_1:'alice_1','bob-2':'bob_2'},updatedAt:serverTimestamp()}));
});
test('AUDIT: Android account cleanup can delete a full 100-message batch',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  const d=c.firestore();const b=writeBatch(d);
  for(let i=0;i<99;i++)b.set(msg(d,'cleanup-'+i),message);await b.commit();
 });
 const d=db('alice');const own=await assertSucceeds(getDocs(query(collection(d,chatPath(ab)+'/messages'),where('sender','==','alice'),limit(100))));
 assert.equal(own.size,100);const b=writeBatch(d);own.forEach(s=>b.delete(s.ref));await assertSucceeds(b.commit());
});

test('P2: exact-pair lookup works for an existing own chat and empty own pair',async()=>{
 const d=db('alice');
 const found=await assertSucceeds(getDocs(query(collection(d,'chats'),where('members','==',['alice','bob']),limit(2))));assert.equal(found.size,1);
 const absent=await assertSucceeds(getDocs(query(collection(d,'chats'),where('members','==',['alice','eve']),limit(2))));assert.equal(absent.empty,true);
 await denied(getDocs(query(collection(db('eve'),'chats'),where('members','==',['alice','bob']),limit(2))));
});

async function openPair(d,a,b) {
 const id=pairId(a,b);
 const found=await getDocs(query(collection(d,'chats'),where('members','==',[a,b].sort()),limit(2)));
 const previous=found.docs.find(s=>s.id!==id)||found.docs[0];
 if(previous)return previous.id;
 await setDoc(doc(d,chatPath(id)),chatPayload(a,b));return id;
}
test('P2: Android open flow creates once and works in both directions',async()=>{
 const id=await assertSucceeds(openPair(db('alice'),'alice','eve'));assert.equal(id,pairId('alice','eve'));
 assert.equal(await assertSucceeds(openPair(db('eve'),'eve','alice')),id);
 const s=await getDocs(query(collection(db('alice'),'chats'),where('members','==',['alice','eve']),limit(2)));assert.equal(s.size,1);
});
test('P2: full Android open flow is safe under simultaneous first opens',async()=>{
 const ids=await assertSucceeds(Promise.all([openPair(db('alice'),'alice','eve'),openPair(db('eve'),'eve','alice')]));
 assert.deepEqual(ids,[pairId('alice','eve'),pairId('alice','eve')]);
});
test('P2: old hashed chat ID and ciphertext remain usable without migration',async()=>{
 const id=legacyId('alice','bob');
 await env.withSecurityRulesDisabled(async c=>{
  const d=c.firestore();await deleteDoc(msg(d));await deleteDoc(doc(d,chatPath(ab)));
  await setDoc(doc(d,chatPath(id)),chat);await setDoc(doc(d,chatPath(id)+'/messages/old'),message);
 });
 const a=db('alice'),b=db('bob');assert.equal(await assertSucceeds(openPair(a,'alice','bob')),id);
 const old=doc(a,chatPath(id)+'/messages/old');assert.equal((await assertSucceeds(getDoc(old))).data().ciphertext,message.ciphertext);
 await assertSucceeds(updateDoc(doc(b,chatPath(id)+'/messages/old'),{readAt:serverTimestamp()}));
 await assertSucceeds(runTransaction(a,async tx=>{
  const next=doc(a,chatPath(id)+'/messages/pending-from-old-app');const snap=await tx.get(next);
  if(!snap.exists())tx.set(next,{...message,createdAt:serverTimestamp()});tx.update(doc(a,chatPath(id)),{updatedAt:serverTimestamp()});
 }));
 await assertSucceeds(deleteDoc(old));await denied(getDocFromServer(doc(db('eve'),chatPath(id))));
 const own=await getDocs(query(collection(a,'chats'),where('members','==',['alice','bob']),limit(2)));assert.equal(own.size,1);assert.equal(own.docs[0].id,id);
});
test('P2: legacy and arbitrary IDs cannot be used for creating new chats',async()=>{
 const d=db('alice');for(const id of [legacyId('alice','eve'),'alice:eve','p2:eve:alice','p2:alice:eve:extra','p2:alice:bob']){
  await denied(setDoc(doc(d,chatPath(id)),chatPayload('alice','eve')));
 }
});
test('P2: delimiter injection is rejected even when profiles were admin-seeded',async()=>{
 const evil='alice:bob';await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'users/'+evil),profile('alice')));
 await denied(setDoc(doc(db(evil),'chats/p2:alice:bob:eve'),{members:[evil,'eve'],names:{[evil]:'alice123',eve:'eve123'},updatedAt:serverTimestamp()}));
});
test('P2: foreign legacy and absent legacy IDs both deny reads and writes',async()=>{
 const old=legacyId('alice','bob');await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),chatPath(old)),chat));
 const d=db('eve');for(const id of [old,legacyId('alice','missing')]){
  await denied(getDocFromServer(doc(d,chatPath(id))));await denied(setDoc(doc(d,chatPath(id)),chatPayload('alice','eve')));
  await denied(getDocFromServer(doc(d,chatPath(id)+'/messages/none')));
 }
});
test('P2: legacy history wins if a duplicate new-format chat already exists',async()=>{
 const id=legacyId('alice','bob');await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),chatPath(id)),chat));
 assert.equal(await assertSucceeds(openPair(db('alice'),'alice','bob')),id);
});
test('P2: unauthorized exact-pair lookups deny both existing and absent foreign pairs',async()=>{
 const d=db('eve');for(const pair of [['alice','bob'],['alice','missing']])
  await denied(getDocs(query(collection(d,'chats'),where('members','==',pair),limit(2))));
});

test('HOME: realtime chat list listener accepts the exact Android query',async()=>{
 const d=db('alice');const q=query(collection(d,'chats'),where('members','array-contains','alice'),orderBy('updatedAt','desc'),limit(50));
 await new Promise((resolve,reject)=>{
  let stop=()=>{};const timeout=setTimeout(()=>{stop();reject(Error('Listener timeout'));},5000);
  stop=onSnapshot(q,{includeMetadataChanges:true},snapshot=>{
   if(snapshot.metadata.fromCache)return;
   try{assert.equal(snapshot.size,1);assert.equal(snapshot.docs[0].id,ab);clearTimeout(timeout);stop();resolve();}
   catch(error){clearTimeout(timeout);stop();reject(error);}
  },error=>{clearTimeout(timeout);stop();reject(error);});
 });
});
test('HOME: realtime listener returns the real permission-denied code for an oversized page',async()=>{
 const d=db('alice');const q=query(collection(d,'chats'),where('members','array-contains','alice'),orderBy('updatedAt','desc'),limit(51));
 await new Promise((resolve,reject)=>{
  let stop=()=>{};const timeout=setTimeout(()=>{stop();reject(Error('Listener timeout'));},5000);
  stop=onSnapshot(q,{includeMetadataChanges:true},snapshot=>{
   if(snapshot.metadata.fromCache)return;
   clearTimeout(timeout);stop();reject(Error('Oversized listener must be denied'));
  },error=>{
   clearTimeout(timeout);stop();try{assert.equal(error.code,'permission-denied');resolve();}catch(failure){reject(failure);}
  });
 });
});
