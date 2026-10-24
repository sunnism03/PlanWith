const test=require('node:test'),assert=require('node:assert/strict');
const {score,aggregate,matched}=require('./metrics.cjs');
const {load,validate,select}=require('./dataset.cjs');
const {gate}=require('./gate.cjs');
const item={title:'독서',date:'2026-10-24',time:null};
const label={test_id:'T1',expected_intent:'TODO',expected_status:'READY',expected_items:[item],label_review_status:'reviewed',split:'holdout'};
const good={intent:'TODO',status:'READY',items:[item],schema_valid:true,business_valid:true,calls:2};
test('exact extraction',()=>assert.equal(score(label,good).full,true));
test('date mismatch',()=>assert.equal(score(label,{...good,items:[{...item,date:'2026-10-25'}]}).dateMatches,0));
test('null and missing time differ',()=>assert.equal(score(label,{...good,items:[{title:'독서',date:item.date}]}).timeMatches,0));
test('missing item',()=>assert.equal(score(label,{...good,items:[]}).full,false));
test('extra item',()=>assert.equal(score(label,{...good,items:[item,item]}).full,false));
test('intent misclassification',()=>assert.equal(score(label,{...good,intent:'EVENT'}).full,false));
test('order independent',()=>assert.equal(matched([item,{...item,title:'운동'}],[{...item,title:'운동'},item],['date','time']),2));
test('duplicate matching consumes predictions once',()=>assert.equal(matched([item,item],[item],['date']),1));
test('duplicate date assignment independent of order',()=>assert.equal(matched([item,{...item,date:'2026-11-01'}],[{...item,date:'2026-11-01'},item],['date']),2));
test('title aliases explicit only',()=>{assert.equal(matched([item],[{...item,title:'책읽기'}],[]),0);assert.equal(matched([{...item,title_aliases:['책읽기']}],[{...item,title:'책읽기'}],[]),1);});
test('macro F1 and confusion matrix',()=>{
 const m=aggregate([{label,output:good},{label:{...label,expected_intent:'EVENT'},output:good}]);
 assert.equal(m.per_class.TODO.precision,.5);assert.equal(m.per_class.TODO.recall,1);assert.equal(m.intent_macro_f1,1/3);assert.equal(m.confusion_matrix.EVENT.TODO,1);
});
test('full success needs business and schema validation',()=>{assert.equal(score(label,{...good,business_valid:false}).full,false);assert.equal(score(label,{...good,schema_valid:false}).full,false);});
test('recovery denominator excludes initial successes',()=>{
 const m=aggregate([{label,output:good},{label,output:{...good,initial_error:'INVALID_DATE_RANGE'}},{label,output:{...good,status:'SCHEMA_INVALID'}}]);
 assert.equal(m.runtime_recovery_rate,.5);assert.equal(m.initial_error_rate,2/3);assert.equal(m.final_failure_rate,1/3);assert.equal(m.cost,null);
});
test('unsupported recurrence is N/A',()=>assert.equal(aggregate([{label,output:good}]).recurrence_accuracy,null));
test('200 cases, honest review status',()=>{const s=validate(load());assert.equal(s.total,200);assert.equal(s.reviewed,0);assert.equal(s.review_required,200);});
test('duplicate and missing labels rejected',()=>{const rows=load();assert.throws(()=>validate([...rows.slice(1),rows[1]]));delete rows[0].expected_time;assert.throws(()=>validate(rows));});
test('unreviewed holdout never enters final evaluation',()=>assert.throws(()=>select(load()),/NOT_RUN/));
test('review needs reviewer evidence',()=>{const rows=load();rows[0].label_review_status='reviewed';assert.throws(()=>validate(rows));});
test('split leakage rejected',()=>{const rows=load();rows[1].scenario_family=rows[0].scenario_family;rows[1].split=rows[0].split==='development'?'holdout':'development';assert.throws(()=>validate(rows));});
function pairs(bad=false){return Array.from({length:3},()=>[{label,output:good,mode:'baseline'},{label,output:bad?{...good,items:[]}:good,mode:'candidate'}]).flat();}
test('normal gate passes',()=>assert.equal(gate(pairs(),['T1']).status,'PASS'));
test('quality regression blocks',()=>assert.equal(gate(pairs(true),['T1']).status,'FAIL'));
test('missing repeats are infrastructure failure',()=>assert.equal(gate(pairs().slice(1),['T1']).status,'INFRASTRUCTURE_FAILURE'));
test('API outage cannot pass',()=>assert.equal(gate([],['T1'],{infrastructureErrors:['API unavailable']}).status,'INFRASTRUCTURE_FAILURE'));
test('no labels cannot pass',()=>assert.equal(gate([],[]).status,'NOT_RUN'));
test('exploration cannot pass final gate',()=>assert.equal(gate(pairs(),['T1'],{exploratory:true}).status,'NOT_A_QUALITY_GATE'));
const {report}=require('./report.cjs');
test('assertion failures with outputs are quality failures, not API outages',()=>{
 const rows=load();const row=rows[0];const result=report({results:{results:[{testCase:{vars:{test_id:row.test_id}},error:'Assertion failed',response:{output:JSON.stringify({...good,mode:'candidate',model:'gpt-4o-mini-2024-07-18',prompt_version:'v1'})}}]}},rows);
 assert.equal(result.metrics.candidate.count,1);
 assert.ok(!result.gate.reasons.some(r=>r.includes('evaluation infrastructure error')));
});
test('zero predicted items has zero precision/recall/F1',()=>{
 const m=aggregate([{label,output:{...good,items:[]}}]); assert.equal(m.item_f1,0); assert.equal(m.item_precision,0); assert.equal(m.item_recall,0);
});
test('mock output cannot pass real-model report',()=>{
 const rows=load(),row=rows[0];const result=report({results:{results:[{testCase:{vars:{test_id:row.test_id}},response:{output:JSON.stringify({...good,mode:'candidate',model:'MOCK'})}}]}},rows);
 assert.equal(result.gate.status,'INFRASTRUCTURE_FAILURE');
});
test('missing provider key is explicitly infrastructure failure',async()=>{
 const Provider=require('./provider.cjs');const saved=process.env.OPENAI_API_KEY;delete process.env.OPENAI_API_KEY;
 try{const out=await new Provider({config:{mode:'candidate'}}).callApi('',{vars:{}});assert.match(out.error,/NOT_RUN/);}finally{if(saved!==undefined)process.env.OPENAI_API_KEY=saved;}
});
test('pre-generation safe exits are not schema failures',()=>{
 const m=aggregate([{label,output:good},{label,output:{...good,schema_valid:null,status:'UNSUPPORTED_RECURRENCE',items:[]}}]);
 assert.equal(m.schema_valid_rate,1);assert.equal(m.denominators.schema_outputs,1);
});
