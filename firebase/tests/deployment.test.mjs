import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('deployment includes the composite index required by the Android chat list',async()=>{
 const indexes=JSON.parse(await readFile('../firestore.indexes.json','utf8')).indexes;
 assert.ok(indexes.some(index=>index.collectionGroup==='chats'&&index.queryScope==='COLLECTION'&&
  index.fields.length===2&&index.fields[0].fieldPath==='members'&&index.fields[0].arrayConfig==='CONTAINS'&&
  index.fields[1].fieldPath==='updatedAt'&&index.fields[1].order==='DESCENDING'));
});
