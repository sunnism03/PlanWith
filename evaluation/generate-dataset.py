"""Synthetic candidate labels, never human-reviewed gold. Boundary-value scenarios, not paraphrase augmentation."""
import json, datetime as dt
from pathlib import Path
for existing in Path('evaluation/data').glob('*.json'):
    if any(r.get('label_review_status')=='reviewed' for r in json.loads(existing.read_text())):
        raise SystemExit('Refusing to overwrite human-reviewed labels. Edit reviewed datasets directly.')
rows=[]
def todo(title,date,time=None): return dict(title=title,date=str(date) if date else None,time=time)
def event(title,date,end=None,start=None,finish=None): return dict(title=title,startDate=str(date),endDate=str(end or date),startTime=start,endTime=finish)
def add(group,text,intent,items,status='READY',reference='2026-10-23',difficulty='medium',error=None):
    i=len(rows)+1
    rows.append(dict(test_id=f'PW-{i:03}', scenario_family=group,user_input=text,reference_date=reference,timezone='Asia/Seoul',expected_intent=intent,expected_items=items,expected_date=[{k:v for k,v in x.items() if 'date' in k.lower()} for x in items],expected_time=[{k:v for k,v in x.items() if 'time' in k.lower()} for x in items],expected_recurrence=None,expected_status=status,difficulty=difficulty,error_category=error,label_review_status='review_required',label_basis='synthetic_draft' if group=='plan' else 'deterministic_calendar_or_policy',reviewer=None,reviewed_at=None,split='holdout' if group in ['calendar','recurrence'] else 'development'))
# Calendar combinations exercise month lengths, leap days, cross-year spans and time-free events.
calendar=['2024-02-29','2028-02-29','2000-02-29','2100-02-28','2026-01-31','2026-02-28','2026-03-31','2026-04-30','2026-05-31','2026-06-30','2026-07-31','2026-08-31','2026-09-30','2026-10-31','2026-11-30','2026-12-31','2027-01-01','2026-01-01','2030-12-31','2025-02-28']
for i,date in enumerate(calendar):
    title=f'{i+1}차 정기검사'
    add('calendar',f'{date}에 {title} 일정 등록해줘','EVENT',[event(title,date)])
    end=dt.date.fromisoformat(date)+dt.timedelta(days=i%4+1)
    title=f'{i+1}차 출장'
    add('calendar',f'{date}부터 {end}까지 {title} 일정 추가','EVENT',[event(title,date,end)])
# Relative days with month/year/leap transitions and explicit clock.
refs=['2026-12-31','2026-02-28','2028-02-28','2028-02-29','2026-01-31','2026-04-30','2026-06-30','2026-09-30','2027-01-01','2026-10-23']
for r in refs:
    for word,offset,title in [('내일',1,'자료 제출'),('모레',2,'독서'),('어제',-1,'운동 기록')]:
        d=dt.date.fromisoformat(r)+dt.timedelta(days=offset)
        add('relative',f'{word} {title} 할 일 추가','TODO',[todo(title,d)],reference=r,error='RELATIVE_DATE')
# Distinct AM/PM boundaries, half-hour and second precision; not just paraphrases.
times=[('오전 12시','00:00:00'),('오후 12시','12:00:00'),('오전 1시','01:00:00'),('오후 1시','13:00:00'),('오전 11시','11:00:00'),('오후 11시','23:00:00'),('정오','12:00:00'),('자정','00:00:00'),('오후 3시 반','15:30:00'),('오전 9시 15분','09:15:00'),('23:59:59','23:59:59'),('00:00:01','00:00:01'),('06:30:00','06:30:00'),('18:45:00','18:45:00'),('오후 10시 5분','22:05:00')]
for i,(text,time) in enumerate(times):
    title=f'{i+1}번 알림 확인'
    add('time',f'2026-11-01 {text}에 {title} 할 일 추가','TODO',[todo(title,'2026-11-01',time)])
    title=f'{i+1}번 온라인 점검'
    add('time',f'2026-11-02 {text}에 {title} 일정 추가. 종료 시각은 미정','EVENT',[event(title,'2026-11-02',start=time)])
# Cardinality, order, duplicates and distinct per-item dates.
for i in range(20):
    n=i%4+2; day=dt.date(2026,11,1)+dt.timedelta(days=i)
    titles=['장보기','우편 발송','보고서 제출','자료 정리','세탁'][:n]
    items=[todo(t,day+dt.timedelta(days=j)) for j,t in enumerate(titles)]
    text=', '.join(f'{x["date"]} {x["title"]}' for x in items)
    add('multiple',text+' 각각 할 일로 추가해줘','TODO',items,difficulty='hard',error='ITEM_CARDINALITY')
ambiguous=[('회의 일정 잡아줘','EVENT'),('보고서 제출 할 일 추가','TODO'),('안녕 반가워','EXCEPTION'),('오늘 날씨를 알려줘','EXCEPTION'),('다음번에 병원 일정','EVENT'),('금요일 3시에 회의 일정','EVENT'),('2026-11-01 5시에 전화 할 일','TODO'),('이번 달 적당한 날 산책 일정','EVENT'),('아무 때나 세탁 할 일','TODO'),('내일인지 모레인지 회의 일정','EVENT'),('2026년 중 여행 일정','EVENT'),('오후 2시부터 회의 일정','EVENT'),('오전 9시 약 복용 할 일','TODO'),('날짜는 모르겠지만 납부 할 일','TODO'),('2026-11-01인지 11-02인지 회의 일정','EVENT'),('그날 책 반납 할 일','TODO'),('이전에 말한 날 면담 일정','EVENT'),('2026-11-01 오후까지 회의 일정','EVENT'),('2026-11-01 아침 적당한 시각에 택배 접수 할 일','TODO'),('농담 하나 들려줘','EXCEPTION')]
for text,intent in ambiguous: add('ambiguous',text,intent,[],'AMBIGUOUS_INPUT',error='AMBIGUOUS_INPUT')
invalid=['2026-02-29','2026-02-30','2026-04-31','2026-06-31','2026-09-31','2026-11-31','2026-13-01','2026-00-10','2026-01-00','2100-02-29']
for date in invalid: add('invalid',f'{date} 회의 일정 추가','EVENT',[],'AMBIGUOUS_INPUT',error='INVALID_DATE')
for start,end in [('17:00','09:00'),('23:00','01:00'),('12:00','11:59'),('09:30','09:00'),('16:00','15:59')]: add('invalid',f'2026-11-01 같은 날 {start}부터 {end}까지 회의 일정','EVENT',[],'AMBIGUOUS_INPUT',error='INVALID_DATE_RANGE')
for time in ['25:00','12:61','24:30','99:00','23:59:61']: add('invalid',f'2026-11-01 {time} 약 복용 할 일','TODO',[],'AMBIGUOUS_INPUT',error='INVALID_TIME')
repeat=['매일','매주 월요일','매주 금요일','격주 수요일','매월 1일','매월 마지막 날','매년 2월 29일','매년 12월 31일','매주 평일','매주 주말','매일 오전 8시','매주 월수금','매월 둘째 화요일','격주 일요일','매년 생일','매주 화목','매일 2번','매월 31일','매년 1월 첫 월요일','매일 자정']
for i,rule in enumerate(repeat):
    add('recurrence',f'2026-11-01부터 {rule} 운동 일정 '+('2027-01-01까지' if i%2 else '10회 반복'),'EVENT',[],'UNSUPPORTED_RECURRENCE',difficulty='hard',error='UNSUPPORTED_RECURRENCE')
    rows[-1]['expected_recurrence']={'supported':False,'rule_text':rule}
goals=['책 완독','자바 기초','이력서 완성','면접 준비','영어 단어 암기','논문 초안','여행 준비','이사 준비','마라톤 준비','자격증 공부','발표 준비','포트폴리오 작성','집 정리','프로젝트 문서화','사진 정리','독서 감상문','알고리즘 학습','기타 연습','요리 연습','전시 준비']
for i,goal in enumerate(goals):
    day=dt.date(2026,11,1)+dt.timedelta(days=i)
    add('plan',f'{day}까지 {goal}를 끝내는 계획 추천해줘','PLAN_RECOMMENDATION',[todo(goal+' 준비','2026-10-24'),todo(goal+' 마무리',day)],difficulty='hard',error='OPEN_ENDED_PLAN')
assert len(rows)==200
# Keep related boundary cases together; hold out entire boundary groups in every domain.
for group in dict.fromkeys(r['scenario_family'] for r in rows):
    subset=[r for r in rows if r['scenario_family']==group]
    group_size={'calendar':2,'relative':3,'time':2}.get(group,1)
    groups=len(subset)//group_size
    cutoff=int(groups*0.7)
    for index,row in enumerate(subset):
        boundary=index//group_size
        row['scenario_family']=f'{group}:boundary-{boundary+1}'
        row['split']='development' if boundary<cutoff else 'holdout'
        row['category']=group
for split in ['development','holdout']:
    Path(f'evaluation/data/{split}.json').write_text(json.dumps([r for r in rows if r['split']==split],ensure_ascii=False,indent=2)+'\n')
