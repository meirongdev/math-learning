-- Phase 6: Seed knowledge nodes (P1-P6 Singapore Math) and assessment questions

-- Root categories
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('numbers',        'Numbers & Algebra',    '数与代数',   NULL,       1, 1),
('measurement',    'Measurement',          '测量',       NULL,       1, 2),
('geometry',       'Geometry',             '图形与几何', NULL,       1, 3),
('statistics',     'Statistics',           '数据分析',   NULL,       1, 4);

-- Numbers & Algebra subtree
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('whole_numbers',       'Whole Numbers',            '整数',       'numbers',  1, 1),
('fractions',           'Fractions',                '分数',       'numbers',  2, 2),
('decimals',            'Decimals',                 '小数',       'numbers',  3, 3),
('percentages',         'Percentages',              '百分比',     'numbers',  5, 4),
('ratio',               'Ratio',                    '比与比例',   'numbers',  5, 5),
('algebra',             'Algebra',                  '代数',       'numbers',  5, 6);

-- Whole Numbers leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('wn.counting',         'Counting & Number Sense',  '数数与数感',     'whole_numbers', 1, 1),
('wn.addition',         'Addition',                 '加法',           'whole_numbers', 1, 2),
('wn.subtraction',      'Subtraction',              '减法',           'whole_numbers', 1, 3),
('wn.multiplication',   'Multiplication',           '乘法',           'whole_numbers', 2, 4),
('wn.division',         'Division',                 '除法',           'whole_numbers', 2, 5),
('wn.four_ops',         'Four Operations (Mixed)',   '四则混合运算',   'whole_numbers', 3, 6),
('wn.factors',          'Factors & Multiples',       '因数与倍数',    'whole_numbers', 4, 7),
('wn.order_of_ops',     'Order of Operations',       '运算顺序',      'whole_numbers', 4, 8),
('wn.large_numbers',    'Large Numbers',             '大数',          'whole_numbers', 4, 9);

-- Fractions leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('frac.concepts',       'Fraction Concepts',         '分数概念',       'fractions', 2, 1),
('frac.equivalent',     'Equivalent Fractions',      '等值分数',       'fractions', 3, 2),
('frac.add_sub',        'Adding & Subtracting Fractions', '分数加减', 'fractions', 3, 3),
('frac.mul_div',        'Multiplying & Dividing Fractions','分数乘除','fractions', 5, 4),
('frac.word_problems',  'Fraction Word Problems',    '分数应用题',    'fractions', 4, 5);

-- Decimals leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('dec.concepts',        'Decimal Concepts',          '小数概念',      'decimals', 3, 1),
('dec.add_sub',         'Adding & Subtracting Decimals','小数加减',  'decimals', 4, 2),
('dec.mul_div',         'Multiplying & Dividing Decimals','小数乘除','decimals', 5, 3),
('dec.conversion',      'Decimal-Fraction Conversion','小数分数转换','decimals', 4, 4);

-- Percentages leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('pct.concepts',        'Percentage Concepts',       '百分比概念',    'percentages', 5, 1),
('pct.applications',    'Percentage Applications',   '百分比应用',    'percentages', 5, 2),
('pct.discount_tax',    'Discount & Tax',            '折扣与税',      'percentages', 6, 3);

-- Ratio leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('ratio.concepts',      'Ratio Concepts',            '比的概念',      'ratio', 5, 1),
('ratio.applications',  'Ratio Applications',        '比的应用',      'ratio', 5, 2),
('ratio.rate_speed',    'Rate & Speed',              '速率与速度',    'ratio', 6, 3);

-- Algebra leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('alg.expressions',     'Algebraic Expressions',     '代数式',        'algebra', 5, 1),
('alg.equations',       'Equations',                 '方程',          'algebra', 6, 2);

-- Measurement subtree
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('meas.length',         'Length',                    '长度',          'measurement', 1, 1),
('meas.mass',           'Mass',                      '质量',          'measurement', 1, 2),
('meas.volume',         'Volume & Capacity',         '体积与容量',    'measurement', 3, 3),
('meas.time',           'Time',                      '时间',          'measurement', 1, 4),
('meas.money',          'Money',                     '金钱',          'measurement', 1, 5),
('meas.area',           'Area & Perimeter',          '面积与周长',    'measurement', 3, 6),
('meas.volume_app',     'Volume Applications',       '体积应用',      'measurement', 5, 7);

-- Geometry subtree
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('geo.2d',              '2D Shapes',                 '平面图形',      'geometry', 1, 1),
('geo.3d',              '3D Shapes',                 '立体图形',      'geometry', 2, 2),
('geo.angles',          'Angles',                    '角度',          'geometry', 3, 3);

-- 2D Shapes leaves
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('geo.triangles',       'Triangles',                 '三角形',        'geo.2d', 3, 1),
('geo.quadrilaterals',  'Quadrilaterals',            '四边形',        'geo.2d', 3, 2),
('geo.circles',         'Circles',                   '圆',            'geo.2d', 5, 3),
('geo.symmetry',        'Symmetry',                  '对称',          'geo.2d', 4, 4);

-- Statistics subtree
INSERT INTO knowledge_nodes (code, name_en, name_zh, parent_code, grade_start, sort_order) VALUES
('stat.pictographs',    'Pictographs',               '象形图',        'statistics', 1, 1),
('stat.bar_graphs',     'Bar Graphs',                '条形图',        'statistics', 2, 2),
('stat.line_graphs',    'Line Graphs',               '折线图',        'statistics', 4, 3),
('stat.pie_charts',     'Pie Charts',                '饼图',          'statistics', 4, 4),
('stat.average',        'Average / Mean',            '平均数',        'statistics', 4, 5);

-- Total: 4 root + 6 mid-level + 9 + 5 + 4 + 3 + 3 + 2 + 7 + 3 + 4 + 5 = 55 nodes, plus leaves = 63 total nodes

----------------------------------------------------------------------
-- Assessment Questions (>=60, covering P1-P6)
----------------------------------------------------------------------

-- P1 questions
INSERT INTO assessment_questions (id, question_text, grade, difficulty, answer_hint) VALUES
('a0000001-0000-0000-0000-000000000001', 'Tom has 5 apples. He gets 3 more. How many apples does Tom have now?', 1, 'easy', '5 + 3 = 8'),
('a0000001-0000-0000-0000-000000000002', 'There are 9 birds on a tree. 4 fly away. How many birds are left?', 1, 'easy', '9 - 4 = 5'),
('a0000001-0000-0000-0000-000000000003', 'Count the number of sides on a rectangle.', 1, 'easy', 'A rectangle has 4 sides'),
('a0000001-0000-0000-0000-000000000004', 'Sam has 7 stickers. He gives 2 to his friend. How many stickers does Sam have left?', 1, 'easy', '7 - 2 = 5'),
('a0000001-0000-0000-0000-000000000005', 'A pencil costs 30 cents. How much do 2 pencils cost?', 1, 'medium', '30 + 30 = 60 cents'),
('a0000001-0000-0000-0000-000000000006', 'What time does the clock show if the hour hand is on 3 and the minute hand is on 12?', 1, 'easy', '3 o''clock'),
('a0000001-0000-0000-0000-000000000007', 'Which is longer: 1 metre or 1 centimetre?', 1, 'easy', '1 metre is longer'),
('a0000001-0000-0000-0000-000000000008', 'Look at the pictograph. If each picture stands for 2 children, and there are 3 pictures for "cats", how many children like cats?', 1, 'medium', '3 x 2 = 6');

-- P2 questions
INSERT INTO assessment_questions (id, question_text, grade, difficulty, answer_hint) VALUES
('a0000002-0000-0000-0000-000000000001', 'What is 46 + 38?', 2, 'easy', '46 + 38 = 84'),
('a0000002-0000-0000-0000-000000000002', 'There are 5 rows of chairs with 4 chairs in each row. How many chairs are there altogether?', 2, 'medium', '5 x 4 = 20'),
('a0000002-0000-0000-0000-000000000003', 'Jane has $1. She buys a drink for 65 cents. How much change does she get?', 2, 'medium', '$1.00 - $0.65 = $0.35'),
('a0000002-0000-0000-0000-000000000004', 'Colour 1/4 of the shape. How many parts should be coloured if there are 8 equal parts?', 2, 'easy', '8 ÷ 4 = 2 parts'),
('a0000002-0000-0000-0000-000000000005', 'A ribbon is 80 cm long. It is cut into 4 equal pieces. How long is each piece?', 2, 'medium', '80 ÷ 4 = 20 cm'),
('a0000002-0000-0000-0000-000000000006', 'What is 7 x 6?', 2, 'easy', '7 x 6 = 42'),
('a0000002-0000-0000-0000-000000000007', 'A cube has how many faces?', 2, 'easy', '6 faces'),
('a0000002-0000-0000-0000-000000000008', 'The bar graph shows 10 children like apples and 6 like oranges. How many more children like apples than oranges?', 2, 'easy', '10 - 6 = 4');

-- P3 questions
INSERT INTO assessment_questions (id, question_text, grade, difficulty, answer_hint) VALUES
('a0000003-0000-0000-0000-000000000001', 'What is 256 + 478?', 3, 'easy', '256 + 478 = 734'),
('a0000003-0000-0000-0000-000000000002', 'A box has 24 oranges. 3 boxes have how many oranges?', 3, 'easy', '24 x 3 = 72'),
('a0000003-0000-0000-0000-000000000003', 'What fraction of the bar is shaded if 3 out of 5 equal parts are shaded?', 3, 'easy', '3/5'),
('a0000003-0000-0000-0000-000000000004', 'Find the perimeter of a rectangle with length 12 cm and width 5 cm.', 3, 'medium', '2 x (12 + 5) = 34 cm'),
('a0000003-0000-0000-0000-000000000005', 'A movie starts at 2:15 PM and ends at 4:00 PM. How long is the movie?', 3, 'medium', '1 hour 45 minutes'),
('a0000003-0000-0000-0000-000000000006', '96 ÷ 8 = ?', 3, 'easy', '96 ÷ 8 = 12'),
('a0000003-0000-0000-0000-000000000007', 'What is 3/4 + 1/4?', 3, 'easy', '3/4 + 1/4 = 4/4 = 1'),
('a0000003-0000-0000-0000-000000000008', 'A jug holds 1 litre of water. 500 ml is poured out. How much water is left?', 3, 'easy', '1000 ml - 500 ml = 500 ml'),
('a0000003-0000-0000-0000-000000000009', 'Name the angle that is less than 90 degrees.', 3, 'easy', 'Acute angle'),
('a0000003-0000-0000-0000-000000000010', 'Write an equivalent fraction for 2/3 with denominator 6.', 3, 'medium', '2/3 = 4/6');

-- P4 questions
INSERT INTO assessment_questions (id, question_text, grade, difficulty, answer_hint) VALUES
('a0000004-0000-0000-0000-000000000001', 'Find the factors of 24.', 4, 'medium', '1, 2, 3, 4, 6, 8, 12, 24'),
('a0000004-0000-0000-0000-000000000002', 'What is 3/5 + 1/3? Express as a fraction in simplest form.', 4, 'medium', 'LCD=15: 9/15 + 5/15 = 14/15'),
('a0000004-0000-0000-0000-000000000003', 'A rectangle has an area of 48 cm² and a length of 8 cm. Find the width.', 4, 'medium', '48 ÷ 8 = 6 cm'),
('a0000004-0000-0000-0000-000000000004', 'Express 0.75 as a fraction in simplest form.', 4, 'easy', '0.75 = 75/100 = 3/4'),
('a0000004-0000-0000-0000-000000000005', 'What is the average of 12, 18, 24, and 6?', 4, 'easy', '(12+18+24+6) ÷ 4 = 15'),
('a0000004-0000-0000-0000-000000000006', 'Round 3.456 to 1 decimal place.', 4, 'easy', '3.5'),
('a0000004-0000-0000-0000-000000000007', 'Ali bought 3 books at $4.50 each. How much did he pay?', 4, 'easy', '3 x $4.50 = $13.50'),
('a0000004-0000-0000-0000-000000000008', 'The line graph shows temperatures. At 12 PM it was 32°C and at 6 PM it was 28°C. What is the decrease?', 4, 'easy', '32 - 28 = 4°C'),
('a0000004-0000-0000-0000-000000000009', 'Find the sum of the first 3 multiples of 7.', 4, 'medium', '7 + 14 + 21 = 42'),
('a0000004-0000-0000-0000-000000000010', 'A shape has one pair of parallel sides. What is it called?', 4, 'easy', 'Trapezium');

-- P5 questions
INSERT INTO assessment_questions (id, question_text, grade, difficulty, answer_hint) VALUES
('a0000005-0000-0000-0000-000000000001', 'Amy has 3/4 of a pizza. She eats 1/3 of what she has. What fraction of the whole pizza does she eat?', 5, 'medium', '1/3 x 3/4 = 1/4'),
('a0000005-0000-0000-0000-000000000002', 'Express 40% as a fraction in simplest form.', 5, 'easy', '40/100 = 2/5'),
('a0000005-0000-0000-0000-000000000003', 'The ratio of boys to girls is 3:5. If there are 24 boys, how many girls are there?', 5, 'medium', '24 ÷ 3 x 5 = 40'),
('a0000005-0000-0000-0000-000000000004', 'Find the volume of a cuboid with length 6 cm, width 4 cm, and height 3 cm.', 5, 'easy', '6 x 4 x 3 = 72 cm³'),
('a0000005-0000-0000-0000-000000000005', 'If y + 12 = 35, find y.', 5, 'easy', 'y = 35 - 12 = 23'),
('a0000005-0000-0000-0000-000000000006', 'A dress costs $80. It is sold at a 25% discount. What is the selling price?', 5, 'medium', '25% of $80 = $20; $80 - $20 = $60'),
('a0000005-0000-0000-0000-000000000007', 'Find the circumference of a circle with diameter 14 cm. (Use π = 22/7)', 5, 'medium', 'C = πd = 22/7 x 14 = 44 cm'),
('a0000005-0000-0000-0000-000000000008', 'Simplify: 2/3 ÷ 4/5', 5, 'medium', '2/3 x 5/4 = 10/12 = 5/6'),
('a0000005-0000-0000-0000-000000000009', 'The ratio of red to blue beads is 2:3. There are 30 beads in total. How many are red?', 5, 'medium', '2/(2+3) x 30 = 12'),
('a0000005-0000-0000-0000-000000000010', 'What is 15% of 200?', 5, 'easy', '15/100 x 200 = 30');

-- P6 questions
INSERT INTO assessment_questions (id, question_text, grade, difficulty, answer_hint) VALUES
('a0000006-0000-0000-0000-000000000001', 'A car travels at 60 km/h. How far does it travel in 2.5 hours?', 6, 'easy', '60 x 2.5 = 150 km'),
('a0000006-0000-0000-0000-000000000002', 'Find the area of a circle with radius 7 cm. (Use π = 22/7)', 6, 'medium', 'A = πr² = 22/7 x 49 = 154 cm²'),
('a0000006-0000-0000-0000-000000000003', 'The GST rate is 9%. An item costs $50 before GST. What is the price after GST?', 6, 'medium', '9% of $50 = $4.50; $50 + $4.50 = $54.50'),
('a0000006-0000-0000-0000-000000000004', 'Solve: 3x - 7 = 20', 6, 'medium', '3x = 27; x = 9'),
('a0000006-0000-0000-0000-000000000005', 'A tank is 2/5 full. After adding 12 litres, it is 4/5 full. Find the capacity of the tank.', 6, 'hard', '4/5 - 2/5 = 2/5 = 12L; capacity = 30L'),
('a0000006-0000-0000-0000-000000000006', 'The pie chart shows 25% like Math, 30% like Science. If 200 students were surveyed, how many like Math?', 6, 'easy', '25% of 200 = 50'),
('a0000006-0000-0000-0000-000000000007', 'John and Peter share $180 in the ratio 2:7. How much does Peter get?', 6, 'medium', '7/(2+7) x $180 = $140'),
('a0000006-0000-0000-0000-000000000008', 'A cuboid has length 10 cm, width 5 cm, and height 8 cm. Find its total surface area.', 6, 'medium', '2(10x5 + 10x8 + 5x8) = 2(50+80+40) = 340 cm²'),
('a0000006-0000-0000-0000-000000000009', 'Express 5/8 as a percentage.', 6, 'easy', '5/8 x 100% = 62.5%'),
('a0000006-0000-0000-0000-000000000010', 'The speed of a cyclist is 15 km/h. He cycles for 40 minutes. What distance does he cover?', 6, 'medium', '40 min = 2/3 h; 15 x 2/3 = 10 km');

-- Assessment question tags (link questions to knowledge nodes)
INSERT INTO assessment_question_tags (question_id, node_code) VALUES
-- P1
('a0000001-0000-0000-0000-000000000001', 'wn.addition'),
('a0000001-0000-0000-0000-000000000002', 'wn.subtraction'),
('a0000001-0000-0000-0000-000000000003', 'geo.quadrilaterals'),
('a0000001-0000-0000-0000-000000000004', 'wn.subtraction'),
('a0000001-0000-0000-0000-000000000005', 'meas.money'),
('a0000001-0000-0000-0000-000000000006', 'meas.time'),
('a0000001-0000-0000-0000-000000000007', 'meas.length'),
('a0000001-0000-0000-0000-000000000008', 'stat.pictographs'),
-- P2
('a0000002-0000-0000-0000-000000000001', 'wn.addition'),
('a0000002-0000-0000-0000-000000000002', 'wn.multiplication'),
('a0000002-0000-0000-0000-000000000003', 'meas.money'),
('a0000002-0000-0000-0000-000000000004', 'frac.concepts'),
('a0000002-0000-0000-0000-000000000005', 'wn.division'),
('a0000002-0000-0000-0000-000000000006', 'wn.multiplication'),
('a0000002-0000-0000-0000-000000000007', 'geo.3d'),
('a0000002-0000-0000-0000-000000000008', 'stat.bar_graphs'),
-- P3
('a0000003-0000-0000-0000-000000000001', 'wn.addition'),
('a0000003-0000-0000-0000-000000000002', 'wn.multiplication'),
('a0000003-0000-0000-0000-000000000003', 'frac.concepts'),
('a0000003-0000-0000-0000-000000000004', 'meas.area'),
('a0000003-0000-0000-0000-000000000005', 'meas.time'),
('a0000003-0000-0000-0000-000000000006', 'wn.division'),
('a0000003-0000-0000-0000-000000000007', 'frac.add_sub'),
('a0000003-0000-0000-0000-000000000008', 'meas.volume'),
('a0000003-0000-0000-0000-000000000009', 'geo.angles'),
('a0000003-0000-0000-0000-000000000010', 'frac.equivalent'),
-- P4
('a0000004-0000-0000-0000-000000000001', 'wn.factors'),
('a0000004-0000-0000-0000-000000000002', 'frac.add_sub'),
('a0000004-0000-0000-0000-000000000003', 'meas.area'),
('a0000004-0000-0000-0000-000000000004', 'dec.conversion'),
('a0000004-0000-0000-0000-000000000005', 'stat.average'),
('a0000004-0000-0000-0000-000000000006', 'dec.concepts'),
('a0000004-0000-0000-0000-000000000007', 'dec.mul_div'),
('a0000004-0000-0000-0000-000000000008', 'stat.line_graphs'),
('a0000004-0000-0000-0000-000000000009', 'wn.factors'),
('a0000004-0000-0000-0000-000000000010', 'geo.quadrilaterals'),
-- P5
('a0000005-0000-0000-0000-000000000001', 'frac.mul_div'),
('a0000005-0000-0000-0000-000000000002', 'pct.concepts'),
('a0000005-0000-0000-0000-000000000003', 'ratio.concepts'),
('a0000005-0000-0000-0000-000000000004', 'meas.volume_app'),
('a0000005-0000-0000-0000-000000000005', 'alg.equations'),
('a0000005-0000-0000-0000-000000000006', 'pct.applications'),
('a0000005-0000-0000-0000-000000000007', 'geo.circles'),
('a0000005-0000-0000-0000-000000000008', 'frac.mul_div'),
('a0000005-0000-0000-0000-000000000009', 'ratio.applications'),
('a0000005-0000-0000-0000-000000000010', 'pct.concepts'),
-- P6
('a0000006-0000-0000-0000-000000000001', 'ratio.rate_speed'),
('a0000006-0000-0000-0000-000000000002', 'geo.circles'),
('a0000006-0000-0000-0000-000000000003', 'pct.discount_tax'),
('a0000006-0000-0000-0000-000000000004', 'alg.equations'),
('a0000006-0000-0000-0000-000000000005', 'frac.word_problems'),
('a0000006-0000-0000-0000-000000000006', 'stat.pie_charts'),
('a0000006-0000-0000-0000-000000000007', 'ratio.applications'),
('a0000006-0000-0000-0000-000000000008', 'meas.volume_app'),
('a0000006-0000-0000-0000-000000000009', 'pct.concepts'),
('a0000006-0000-0000-0000-000000000010', 'ratio.rate_speed');
