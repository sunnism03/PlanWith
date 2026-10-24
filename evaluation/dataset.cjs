const fs=require('node:fs'),path=require('node:path');
function load() { return ['development','holdout'].flatMap(s=>JSON.parse(fs.readFileSync(path.join(__dirname,'data',s+'.json'),'utf8'))); }
function validDate(s) { return typeof s==='string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && Number.isFinite(Date.parse(s)) && new Date(s).toISOString().slice(0,10)===s; }
function validate(rows) {
  const ids=new Set(),inputs=new Set(),families=new Map();
  if(rows.length!==200) throw Error('Exactly 200 candidate cases required');
  for(const r of rows) {
    for(const k of ['test_id','user_input','reference_date','timezone','expected_intent','expected_items','expected_date','expected_time','expected_recurrence','expected_status','difficulty','error_category','label_review_status','split','scenario_family']) if(!Object.hasOwn(r,k)) throw Error(`Missing ${k}: ${r.test_id}`);
    const identity=JSON.stringify([r.user_input.normalize('NFC').trim(),r.reference_date,r.timezone]);
    if(ids.has(r.test_id)||inputs.has(identity)) throw Error('Duplicate ID or input/date/zone'); ids.add(r.test_id); inputs.add(identity);
    if(!validDate(r.reference_date)||r.timezone!=='Asia/Seoul') throw Error('Invalid reference');
    if(!['EVENT','TODO','PLAN_RECOMMENDATION','EXCEPTION'].includes(r.expected_intent)||!['READY','AMBIGUOUS_INPUT','UNSUPPORTED_RECURRENCE'].includes(r.expected_status)) throw Error('Invalid intent/status');
    if(!['review_required','reviewed'].includes(r.label_review_status)) throw Error('Invalid review state');
    if(r.label_review_status==='reviewed' && (!r.reviewer||!r.reviewed_at||!validDate(r.reviewed_at))) throw Error('Human reviewer/date required');
    if(!['development','holdout'].includes(r.split)) throw Error('Invalid split');
    if(families.has(r.scenario_family)&&families.get(r.scenario_family)!==r.split) throw Error('Scenario family leaks across splits'); families.set(r.scenario_family,r.split);
    if(!Array.isArray(r.expected_items)||(r.expected_status==='READY')!==(r.expected_items.length>0)) throw Error('Status/item inconsistency');
    const dates=r.expected_items.map(x=>Object.fromEntries(Object.entries(x).filter(([k])=>/date/i.test(k))));
    const times=r.expected_items.map(x=>Object.fromEntries(Object.entries(x).filter(([k])=>/time/i.test(k))));
    if(JSON.stringify(dates)!==JSON.stringify(r.expected_date)||JSON.stringify(times)!==JSON.stringify(r.expected_time))throw Error('Inconsistent projected date/time labels');
    if(r.expected_status==='UNSUPPORTED_RECURRENCE'&&r.expected_recurrence?.supported!==false)throw Error('Missing recurrence support label');
    for(const item of r.expected_items) {
      if(typeof item.title!=='string'||!item.title.trim()||item.title.length>255) throw Error('Invalid title');
      const dateKeys=r.expected_intent==='EVENT'?['startDate','endDate']:['date'];
      const timeKeys=r.expected_intent==='EVENT'?['startTime','endTime']:['time'];
      for(const k of dateKeys) if(!validDate(item[k])) throw Error('Invalid label date');
      for(const k of timeKeys) if(item[k]!==null && (typeof item[k]!=='string'||!/^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/.test(item[k]))) throw Error('Invalid label time');
      if(r.expected_intent==='EVENT'&&(item.startDate>item.endDate||(item.startDate===item.endDate&&item.startTime&&item.endTime&&item.startTime>item.endTime))) throw Error('Invalid range');
    }
  }
  return {total:rows.length,reviewed:rows.filter(x=>x.label_review_status==='reviewed').length,review_required:rows.filter(x=>x.label_review_status!=='reviewed').length,distribution:Object.fromEntries([...new Set(rows.map(r=>r.category))].map(f=>[f,rows.filter(r=>r.category===f).length]))};
}
function select(rows,exploratory=false) {
  validate(rows);
  const selected=rows.filter(r=>r.split===(exploratory?'development':'holdout')&&(exploratory||r.label_review_status==='reviewed'));
  if(!selected.length) throw Error('NOT_RUN: no human-reviewed holdout labels');
  if(!exploratory && selected.length!==rows.filter(r=>r.split==='holdout').length) throw Error('NOT_RUN: all holdout labels must be reviewed before final evaluation');
  return selected;
}
if(require.main===module) console.log(JSON.stringify(validate(load()),null,2));
module.exports={load,validate,select,validDate};
