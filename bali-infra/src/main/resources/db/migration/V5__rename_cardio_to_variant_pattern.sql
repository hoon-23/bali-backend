-- 유산소 종목을 다른 종목들과 동일한 "기본 이름 + variant" 패턴으로 통일
-- 기존: (실내달리기, variant=NULL), (실내걷기, variant=NULL)
-- 변경: (유산소, variant=달리기), (유산소, variant=걷기)
UPDATE exercises SET name = '유산소', variant = '달리기' WHERE name = '실내달리기' AND muscle_group = 'CARDIO';
UPDATE exercises SET name = '유산소', variant = '걷기' WHERE name = '실내걷기' AND muscle_group = 'CARDIO';
