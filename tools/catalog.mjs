// The curated exercise catalog. Every entry becomes one record in
// app/src/main/assets/content/exercises.json via import-exercises.mjs.
//
// An entry with `src` borrows instructions, muscles, level and photos from free-exercise-db
// (public domain, https://github.com/yuhonas/free-exercise-db); anything given here wins.
// An entry without `src` is written from scratch; its photos come from photos.mjs.
//
// measure: "30s" = timed, "x12" = reps. With perSide, the measure applies to each side.
// met: metabolic equivalent used for the calorie estimate (Compendium of Physical Activities
// ballparks: stretching 2.3, light calisthenics 3.5-3.8, moderate 5.0, vigorous 8.0).

const STRETCH = { category: 'STRETCH', met: 2.3 };
const CORE = { category: 'STRENGTH', met: 3.8 };
const MODERATE = { category: 'STRENGTH', met: 5.0 };
const VIGOROUS = { category: 'CARDIO', met: 8.0 };
const PLYO = { category: 'PLYOMETRIC', met: 8.0, noJump: false };
const LIGHT = { category: 'CARDIO', met: 3.0 };

const e = (id, src, name, measure, opts = {}) => ({ id, src, name, measure, ...opts });

export const catalog = [
  // ---------------------------------------------------------------- Cardio and warm-up
  e('jumping_jacks', null, 'Jumping Jacks', '30s', {
    ...VIGOROUS, noJump: false, level: 'BEGINNER', focus: ['FULL_BODY'], harder: 'star_jumps',
    easier: 'march_in_place', primaryMuscles: ['quadriceps'], secondaryMuscles: ['calves', 'shoulders'],
    instructions: [
      'Stand with your feet together and your arms by your sides.',
      'Jump your feet out to slightly wider than your shoulders while sweeping your arms overhead.',
      'Jump back to the start, bringing your arms down to your sides.',
      'Keep a steady rhythm and land softly on the balls of your feet.',
    ],
    tips: ['Land softly with your knees slightly bent.', 'Breathe in rhythm with the jumps.'],
  }),
  e('high_knees', null, 'High Knees', '30s', {
    ...VIGOROUS, noJump: false, level: 'BEGINNER', focus: ['FULL_BODY', 'LEGS'], easier: 'march_in_place',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['abdominals', 'calves'],
    instructions: [
      'Stand tall with your feet hip-width apart.',
      'Run on the spot, driving one knee up to hip height, then the other.',
      'Pump your arms in time with your legs.',
      'Stay on the balls of your feet and keep your chest up.',
    ],
    tips: ['Lean back slightly rather than hunching forward.'],
  }),
  e('butt_kicks', 'Single_Leg_Butt_Kick', 'Butt Kicks', '30s', {
    ...VIGOROUS, noJump: false, focus: ['FULL_BODY', 'LEGS'],
    instructions: [
      'Stand tall and jog on the spot.',
      'Kick each heel up towards your glutes as you go.',
      'Keep your knees pointing down and your arms pumping.',
    ],
  }),
  e('burpees', null, 'Burpees', 'x10', {
    ...VIGOROUS, noJump: false, level: 'INTERMEDIATE', focus: ['FULL_BODY'], easier: 'squat_thrusts',
    primaryMuscles: ['quadriceps', 'chest'], secondaryMuscles: ['shoulders', 'abdominals', 'triceps'],
    instructions: [
      'Stand with your feet shoulder-width apart.',
      'Squat down and place your hands on the floor in front of you.',
      'Jump your feet back into a plank, then do one push-up.',
      'Jump your feet back towards your hands, then jump up with your arms overhead.',
    ],
    tips: ['Keep your core tight in the plank so your hips do not sag.'],
  }),
  e('squat_thrusts', null, 'Squat Thrusts', 'x10', {
    ...VIGOROUS, noJump: true, level: 'BEGINNER', focus: ['FULL_BODY'], harder: 'burpees',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['shoulders', 'abdominals'],
    instructions: [
      'Stand with your feet shoulder-width apart.',
      'Squat down and place your hands on the floor.',
      'Step your feet back one at a time into a plank.',
      'Step them back in and stand up.',
    ],
  }),
  e('mountain_climbers', 'Mountain_Climbers', 'Mountain Climbers', '30s', {
    ...VIGOROUS, noJump: true, focus: ['ABS', 'FULL_BODY'],
    instructions: [
      'Start in a high plank with your hands under your shoulders.',
      'Drive one knee towards your chest, then switch legs quickly.',
      'Keep your hips level and your back flat throughout.',
    ],
    tips: ['Go slower and keep good form rather than rushing.'],
  }),
  e('star_jumps', 'Star_Jump', 'Star Jumps', 'x12', {
    ...PLYO, focus: ['FULL_BODY'], easier: 'jumping_jacks',
  }),
  e('squat_jumps', 'Freehand_Jump_Squat', 'Jump Squats', 'x12', {
    ...PLYO, focus: ['LEGS', 'BUTT'], easier: 'squats',
    instructions: [
      'Stand with your feet shoulder-width apart.',
      'Squat down until your thighs are close to parallel with the floor.',
      'Explode upwards into a jump, swinging your arms for momentum.',
      'Land softly with bent knees and go straight into the next squat.',
    ],
  }),
  e('split_jumps', 'Split_Jump', 'Split Jumps', 'x12', {
    ...PLYO, focus: ['LEGS', 'BUTT'], easier: 'reverse_lunges',
  }),
  e('tuck_jumps', 'Knee_Tuck_Jump', 'Tuck Jumps', 'x8', {
    ...PLYO, level: 'ADVANCED', focus: ['LEGS', 'FULL_BODY'], easier: 'squat_jumps',
  }),
  e('skater_hops', 'Lateral_Bound', 'Skater Hops', '30s', {
    ...PLYO, focus: ['LEGS', 'BUTT'],
    instructions: [
      'Stand on your right leg with a slight bend in the knee.',
      'Leap sideways to the left, landing softly on your left foot.',
      'Swing your right leg behind you and touch down lightly if you need balance.',
      'Leap back to the right and keep alternating.',
    ],
  }),
  e('shadow_boxing', null, 'Shadow Boxing', '30s', {
    category: 'CARDIO', met: 6.0, noJump: true, level: 'BEGINNER', focus: ['ARMS', 'FULL_BODY'],
    primaryMuscles: ['shoulders'], secondaryMuscles: ['triceps', 'abdominals'],
    instructions: [
      'Stand in a boxing stance: feet staggered, knees soft, fists by your chin.',
      'Throw fast straight punches, alternating arms.',
      'Rotate through your torso with each punch and bring the fist straight back to your chin.',
    ],
    tips: ['Do not lock your elbows at full extension.'],
  }),
  e('march_in_place', null, 'March on the Spot', '30s', {
    ...LIGHT, noJump: true, level: 'BEGINNER', focus: ['FULL_BODY'], harder: 'high_knees',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['calves'],
    instructions: [
      'Stand tall with your arms by your sides.',
      'March on the spot, lifting each knee towards hip height.',
      'Swing your arms naturally in time with your steps.',
    ],
  }),
  e('inchworm', 'Inchworm', 'Inchworms', 'x6', {
    category: 'STRENGTH', met: 4.0, focus: ['FULL_BODY', 'SHOULDERS_BACK'],
    instructions: [
      'Stand tall, then bend forward and place your hands on the floor, bending your knees as needed.',
      'Walk your hands forward until you are in a high plank.',
      'Walk your feet up towards your hands, then roll back up to standing.',
    ],
  }),
  e('step_up_knee_raise', 'Step-up_with_Knee_Raise', 'Step-Up with Knee Raise', 'x10', {
    ...MODERATE, perSide: true, equipment: 'CHAIR', focus: ['LEGS', 'BUTT'],
    tips: ['Use a sturdy step or a chair pushed against a wall.'],
  }),
  e('plank_jacks', null, 'Plank Jacks', '30s', {
    ...VIGOROUS, noJump: false, level: 'INTERMEDIATE', focus: ['ABS', 'FULL_BODY'],
    primaryMuscles: ['abdominals'], secondaryMuscles: ['shoulders', 'adductors'],
    instructions: [
      'Start in a high plank with your feet together.',
      'Jump your feet out wide, then back together, like a horizontal jumping jack.',
      'Keep your hips level and your shoulders over your wrists.',
    ],
  }),
  e('spider_crawl', 'Spider_Crawl', 'Spider Crawl', 'x10', {
    ...MODERATE, level: 'ADVANCED', focus: ['ABS', 'FULL_BODY'],
  }),

  // ---------------------------------------------------------------- Running (outdoors or treadmill)
  e('brisk_walk', null, 'Brisk Walk', '60s', {
    category: 'CARDIO', met: 4.3, noJump: true, level: 'BEGINNER', equipment: 'OUTDOORS', focus: ['LEGS', 'FULL_BODY'],
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['calves', 'glutes'],
    instructions: [
      'Walk at a pace where you can talk but not sing.',
      'Swing your arms naturally and stand tall.',
      'Use it to recover between running intervals.',
    ],
  }),
  e('easy_run', null, 'Easy Run', '60s', {
    category: 'CARDIO', met: 8.3, noJump: false, level: 'BEGINNER', equipment: 'OUTDOORS', focus: ['LEGS', 'FULL_BODY'], harder: 'fast_run', easier: 'brisk_walk',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['hamstrings', 'calves', 'glutes'],
    instructions: [
      'Run at a relaxed, conversational pace.',
      'Take short, light steps and land under your hips rather than out in front.',
      'Keep your shoulders relaxed and breathe steadily.',
    ],
    tips: ['If you can\'t speak in full sentences, slow down.'],
  }),
  e('fast_run', null, 'Fast Run', '30s', {
    category: 'CARDIO', met: 11.0, noJump: false, level: 'INTERMEDIATE', equipment: 'OUTDOORS', focus: ['LEGS', 'FULL_BODY'], easier: 'easy_run',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['hamstrings', 'calves', 'glutes'],
    instructions: [
      'Pick up the pace to a hard but controlled effort.',
      'Drive your arms and lift your knees a little higher.',
      'Hold good form; ease off if it starts to fall apart.',
    ],
  }),

  // ---------------------------------------------------------------- Abs
  e('crunches', 'Crunches', 'Crunches', 'x15', {
    ...CORE, focus: ['ABS'], harder: 'tuck_crunch',
    instructions: [
      'Lie on your back with your knees bent and feet flat on the floor.',
      'Place your hands lightly behind your head or across your chest.',
      'Curl your shoulders off the floor by contracting your abs, then lower slowly.',
    ],
    tips: ['Do not pull on your neck; your hands are only there for support.'],
  }),
  e('crunch_overhead', 'Crunch_-_Hands_Overhead', 'Overhead Crunches', 'x12', {
    ...CORE, focus: ['ABS'], easier: 'crunches',
  }),
  e('bicycle_crunches', 'Air_Bike', 'Bicycle Crunches', 'x16', {
    ...CORE, focus: ['ABS'], easier: 'heel_touches',
  }),
  e('heel_touches', 'Alternate_Heel_Touchers', 'Heel Touches', 'x20', {
    ...CORE, level: 'BEGINNER', focus: ['ABS'], harder: 'bicycle_crunches',
  }),
  e('russian_twist', 'Russian_Twist', 'Russian Twists', 'x20', {
    ...CORE, focus: ['ABS'],
    instructions: [
      'Sit on the floor with your knees bent and lean back slightly with a straight back.',
      'Clasp your hands in front of your chest.',
      'Rotate your torso to bring your hands beside one hip, then twist to the other side.',
    ],
    tips: ['Keep your back straight; lift your feet only when you can keep it straight.'],
  }),
  e('plank', 'Plank', 'Plank', '30s', {
    ...CORE, focus: ['ABS', 'FULL_BODY'], harder: 'plank_jacks', easier: 'knee_plank',
    instructions: [
      'Rest on your forearms and toes, elbows directly under your shoulders.',
      'Lift your hips so your body forms a straight line from head to heels.',
      'Brace your abs and squeeze your glutes. Hold, breathing steadily.',
    ],
    tips: ['Do not let your hips sag or pike up.', 'Look at the floor to keep your neck neutral.'],
  }),
  e('knee_plank', null, 'Knee Plank', '30s', {
    ...CORE, level: 'BEGINNER', focus: ['ABS'], harder: 'plank',
    primaryMuscles: ['abdominals'], secondaryMuscles: ['shoulders'],
    instructions: [
      'Rest on your forearms and knees, elbows under your shoulders.',
      'Keep a straight line from your head to your knees.',
      'Brace your abs and hold, breathing steadily.',
    ],
  }),
  e('side_plank', 'Side_Bridge', 'Side Plank', '20s', {
    ...CORE, perSide: true, focus: ['ABS'],
    instructions: [
      'Lie on your side with your elbow under your shoulder and your legs stacked.',
      'Lift your hips until your body is straight from head to feet.',
      'Hold, then switch sides.',
    ],
  }),
  e('reverse_crunches', 'Reverse_Crunch', 'Reverse Crunches', 'x12', { ...CORE, focus: ['ABS'] }),
  e('leg_raises', null, 'Leg Raises', 'x12', {
    ...CORE, level: 'INTERMEDIATE', focus: ['ABS'], easier: 'reverse_crunches',
    primaryMuscles: ['abdominals'], secondaryMuscles: ['quadriceps'],
    instructions: [
      'Lie on your back with your legs straight and your hands under your hips.',
      'Keeping your legs together, raise them until they point at the ceiling.',
      'Lower them slowly until they hover just above the floor, then repeat.',
    ],
    tips: ['Press your lower back into the floor the whole time.'],
  }),
  e('flutter_kicks', null, 'Flutter Kicks', '30s', {
    ...CORE, level: 'BEGINNER', focus: ['ABS'],
    primaryMuscles: ['abdominals'], secondaryMuscles: ['quadriceps'],
    instructions: [
      'Lie on your back with your legs straight and your hands under your hips.',
      'Lift both heels a few centimetres off the floor.',
      'Kick your legs up and down in small, quick alternating movements.',
    ],
    tips: ['Press your lower back into the floor; raise your legs higher if it arches.'],
  }),
  e('scissor_kicks', 'Scissor_Kick', 'Scissor Kicks', '30s', { ...CORE, category: 'STRENGTH', focus: ['ABS'] }),
  e('dead_bug', 'Dead_Bug', 'Dead Bug', 'x12', { ...CORE, level: 'BEGINNER', focus: ['ABS'] }),
  e('sit_ups', 'Sit-Up', 'Sit-Ups', 'x12', {
    ...CORE, focus: ['ABS'], easier: 'crunches',
    instructions: [
      'Lie on your back with your knees bent and feet flat; tuck your feet under a sofa if you need to.',
      'Cross your arms over your chest.',
      'Curl all the way up until your chest is close to your thighs, then lower slowly.',
    ],
  }),
  e('v_ups', 'Jackknife_Sit-Up', 'V-Ups', 'x10', {
    ...CORE, level: 'ADVANCED', focus: ['ABS'], easier: 'sit_ups',
  }),
  e('elbow_to_knee', 'Elbow_to_Knee', 'Elbow to Knee Crunches', 'x16', { ...CORE, focus: ['ABS'] }),
  e('oblique_crunches', 'Oblique_Crunches_-_On_The_Floor', 'Oblique Crunches', 'x12', {
    ...CORE, perSide: true, focus: ['ABS'],
  }),
  e('butt_ups', 'Butt-Ups', 'Butt-Ups', 'x12', { ...CORE, focus: ['ABS'] }),
  e('cocoons', 'Cocoons', 'Cocoons', 'x12', { ...CORE, level: 'INTERMEDIATE', focus: ['ABS'] }),
  e('tuck_crunch', 'Tuck_Crunch', 'Tuck Crunches', 'x12', { ...CORE, focus: ['ABS'], easier: 'crunches' }),
  e('hip_raise', 'Bent-Knee_Hip_Raise', 'Bent-Knee Hip Raise', 'x12', { ...CORE, focus: ['ABS'] }),
  e('toe_touch_crunch', 'Toe_Touchers', 'Toe Touch Crunches', 'x12', { ...CORE, category: 'STRENGTH', focus: ['ABS'] }),
  e('push_up_side_plank', 'Push_Up_to_Side_Plank', 'Push-Up to Side Plank', 'x8', {
    ...MODERATE, level: 'ADVANCED', focus: ['CHEST', 'ABS'],
  }),
  e('bird_dog', null, 'Bird Dog', 'x10', {
    ...CORE, perSide: true, level: 'BEGINNER', focus: ['ABS', 'SHOULDERS_BACK'],
    primaryMuscles: ['lower back'], secondaryMuscles: ['abdominals', 'glutes'],
    instructions: [
      'Kneel on all fours with your hands under your shoulders and knees under your hips.',
      'Reach your right arm forward and your left leg back until both are level with your torso.',
      'Pause, return to all fours, then repeat on the other side.',
    ],
    tips: ['Move slowly; your hips should not rock side to side.'],
  }),

  // ---------------------------------------------------------------- Chest
  e('push_ups', 'Pushups', 'Push-Ups', 'x10', {
    ...MODERATE, focus: ['CHEST', 'ARMS'], easier: 'knee_push_ups', harder: 'decline_push_ups',
    instructions: [
      'Start in a high plank with your hands slightly wider than your shoulders.',
      'Lower your chest towards the floor, keeping your body in one straight line.',
      'Push back up until your arms are straight.',
    ],
    tips: ['Keep your elbows at about 45 degrees from your body.', 'Breathe in on the way down, out on the way up.'],
  }),
  e('knee_push_ups', null, 'Knee Push-Ups', 'x10', {
    ...MODERATE, level: 'BEGINNER', focus: ['CHEST', 'ARMS'], easier: 'wall_push_ups', harder: 'push_ups',
    primaryMuscles: ['chest'], secondaryMuscles: ['triceps', 'shoulders'],
    instructions: [
      'Start on your hands and knees, hands slightly wider than your shoulders.',
      'Walk your hands forward until your body is straight from head to knees.',
      'Lower your chest towards the floor, then push back up.',
    ],
  }),
  e('wall_push_ups', null, 'Wall Push-Ups', 'x12', {
    ...LIGHT, category: 'STRENGTH', noJump: true, level: 'BEGINNER', equipment: 'WALL', focus: ['CHEST', 'ARMS'],
    harder: 'incline_push_ups', primaryMuscles: ['chest'], secondaryMuscles: ['triceps', 'shoulders'],
    instructions: [
      'Stand an arm\'s length from a wall and place your palms on it at shoulder height.',
      'Bend your elbows to bring your chest towards the wall.',
      'Push back to the start, keeping your body straight.',
    ],
  }),
  e('incline_push_ups', 'Incline_Push-Up', 'Incline Push-Ups', 'x10', {
    ...MODERATE, level: 'BEGINNER', equipment: 'CHAIR', focus: ['CHEST', 'ARMS'], harder: 'push_ups',
    tips: ['Use a sturdy chair, sofa arm or kitchen counter.'],
  }),
  e('wide_push_ups', 'Push-Up_Wide', 'Wide Push-Ups', 'x10', { ...MODERATE, focus: ['CHEST'], easier: 'knee_push_ups' }),
  e('decline_push_ups', 'Decline_Push-Up', 'Decline Push-Ups', 'x10', {
    ...MODERATE, level: 'INTERMEDIATE', equipment: 'CHAIR', focus: ['CHEST', 'SHOULDERS_BACK'], easier: 'push_ups',
  }),
  e('diamond_push_ups', 'Push-Ups_-_Close_Triceps_Position', 'Diamond Push-Ups', 'x8', {
    ...MODERATE, level: 'INTERMEDIATE', focus: ['ARMS', 'CHEST'], easier: 'knee_push_ups',
  }),
  e('close_wide_push_ups', 'Pushups_Close_and_Wide_Hand_Positions', 'Close and Wide Push-Ups', 'x10', {
    ...MODERATE, level: 'INTERMEDIATE', focus: ['CHEST', 'ARMS'],
  }),
  e('plyo_push_ups', 'Plyo_Push-up', 'Clap Push-Ups', 'x8', {
    ...PLYO, level: 'ADVANCED', focus: ['CHEST'], easier: 'push_ups',
  }),
  e('chest_squeeze', 'Isometric_Chest_Squeezes', 'Isometric Chest Squeeze', '30s', {
    category: 'STRENGTH', met: 2.8, noJump: true, focus: ['CHEST'],
  }),
  e('dynamic_chest_stretch', 'Dynamic_Chest_Stretch', 'Dynamic Chest Stretch', '30s', { ...STRETCH, focus: ['CHEST'] }),

  // ---------------------------------------------------------------- Arms
  e('triceps_dips', 'Bench_Dips', 'Triceps Dips', 'x10', {
    ...MODERATE, equipment: 'CHAIR', focus: ['ARMS'], easier: 'floor_triceps_dips',
    instructions: [
      'Sit on the edge of a sturdy chair and grip the edge beside your hips.',
      'Slide your hips off the chair with your legs out in front of you.',
      'Bend your elbows to lower your body, then press back up.',
    ],
    tips: ['Keep your back close to the chair and your shoulders down away from your ears.'],
  }),
  e('floor_triceps_dips', null, 'Floor Triceps Dips', 'x10', {
    ...MODERATE, level: 'BEGINNER', focus: ['ARMS'], harder: 'triceps_dips',
    primaryMuscles: ['triceps'], secondaryMuscles: ['shoulders'],
    instructions: [
      'Sit on the floor with your knees bent and your hands behind you, fingers pointing to your feet.',
      'Lift your hips off the floor.',
      'Bend your elbows to lower your body, then straighten them to push back up.',
    ],
  }),
  e('arm_circles', 'Arm_Circles', 'Arm Circles', '30s', {
    ...LIGHT, category: 'STRETCH', focus: ['ARMS', 'SHOULDERS_BACK'],
    instructions: [
      'Stand with your arms out to the sides at shoulder height.',
      'Make small circles forwards, gradually making them bigger.',
      'Halfway through, reverse the direction.',
    ],
  }),
  // The dataset's version needs a partner holding the towel; this is the solo one.
  e('towel_triceps_extension', 'Standing_Towel_Triceps_Extension', 'Towel Triceps Extension', 'x10', {
    ...MODERATE, perSide: true, focus: ['ARMS'],
    instructions: [
      'Stand tall holding one end of a towel in one hand, elbow bent and pointing up, so the towel hangs down your back.',
      'Reach behind your lower back with the other hand and grab the bottom of the towel.',
      'Keeping your upper arm beside your head, straighten the top arm while the lower hand pulls down against it.',
      'Lower slowly. Finish the reps, then switch arms.',
    ],
    tips: ['Pull harder with the lower hand to make it tougher.', 'Keep your elbow still; only the forearm moves.'],
  }),
  e('arm_raises', null, 'Arm Raises', '30s', {
    ...LIGHT, category: 'STRENGTH', noJump: true, level: 'BEGINNER', focus: ['ARMS', 'SHOULDERS_BACK'],
    primaryMuscles: ['shoulders'], secondaryMuscles: [],
    instructions: [
      'Stand with your feet hip-width apart and arms by your sides.',
      'Raise your straight arms in front of you up to overhead.',
      'Lower them slowly and repeat at a steady pace.',
    ],
  }),
  e('leg_assisted_curl', null, 'Leg-Resisted Biceps Curl', 'x10', {
    ...CORE, perSide: true, level: 'BEGINNER', focus: ['ARMS'],
    primaryMuscles: ['biceps'], secondaryMuscles: ['forearms'],
    instructions: [
      'Sit on the floor or a chair and hold behind one knee with both hands.',
      'Let that leg relax so it acts as the weight, and curl it up by bending your elbows.',
      'Lower slowly. Push down with the leg a little to make it harder. Switch sides.',
    ],
  }),
  e('plank_up_downs', null, 'Plank Up-Downs', 'x10', {
    ...MODERATE, noJump: true, level: 'INTERMEDIATE', focus: ['ARMS', 'ABS'],
    primaryMuscles: ['triceps'], secondaryMuscles: ['abdominals', 'shoulders'],
    instructions: [
      'Start in a forearm plank.',
      'Press up onto one hand, then the other, until you are in a high plank.',
      'Lower back down one forearm at a time. Alternate the leading arm each rep.',
    ],
    tips: ['Widen your feet to stop your hips swaying.'],
  }),
  e('biceps_stretch', null, 'Standing Biceps Stretch', '20s', {
    ...STRETCH, level: 'BEGINNER', focus: ['ARMS', 'CHEST'],
    primaryMuscles: ['biceps'], secondaryMuscles: ['chest', 'shoulders'],
    instructions: [
      'Stand tall and interlace your fingers behind your back.',
      'Straighten your arms and turn your palms down.',
      'Gently lift your arms until you feel a stretch in your biceps and chest. Hold.',
    ],
  }),
  e('side_arm_raises', null, 'Side Arm Raises', '30s', {
    ...LIGHT, category: 'STRENGTH', noJump: true, level: 'BEGINNER', focus: ['ARMS', 'SHOULDERS_BACK'],
    primaryMuscles: ['shoulders'], secondaryMuscles: [],
    instructions: [
      'Stand tall with your arms by your sides.',
      'Raise your arms out to the sides until they are level with your shoulders.',
      'Lower slowly with control and repeat.',
    ],
  }),

  // ---------------------------------------------------------------- Legs and glutes
  e('squats', 'Bodyweight_Squat', 'Squats', 'x15', {
    ...MODERATE, focus: ['LEGS', 'BUTT'], easier: 'chair_squats', harder: 'squat_jumps',
    instructions: [
      'Stand with your feet shoulder-width apart, toes turned out slightly.',
      'Push your hips back and bend your knees as if sitting into a chair.',
      'Go as low as you comfortably can, keeping your chest up.',
      'Drive through your heels to stand back up.',
    ],
    tips: ['Keep your knees in line with your toes.', 'Your heels stay on the floor the whole time.'],
  }),
  e('chair_squats', null, 'Chair Sit-to-Stand', 'x12', {
    ...LIGHT, category: 'STRENGTH', noJump: true, level: 'BEGINNER', equipment: 'CHAIR', focus: ['LEGS', 'BUTT'],
    harder: 'squats', primaryMuscles: ['quadriceps'], secondaryMuscles: ['glutes'],
    instructions: [
      'Sit near the front of a sturdy chair with your feet flat and hip-width apart.',
      'Lean forward slightly and stand up without using your hands.',
      'Slowly lower yourself back down until you lightly touch the seat.',
    ],
  }),
  e('walking_lunges', 'Bodyweight_Walking_Lunge', 'Walking Lunges', 'x10', {
    ...MODERATE, perSide: true, focus: ['LEGS', 'BUTT'], easier: 'reverse_lunges',
  }),
  e('reverse_lunges', null, 'Reverse Lunges', 'x10', {
    ...MODERATE, noJump: true, perSide: true, level: 'BEGINNER', focus: ['LEGS', 'BUTT'], harder: 'split_jumps',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['glutes', 'hamstrings'],
    instructions: [
      'Stand tall with your feet hip-width apart.',
      'Step one foot back and lower until both knees are bent at about 90 degrees.',
      'Push through your front heel to return to standing.',
    ],
    tips: ['Keep your front knee over your ankle.'],
  }),
  e('side_lunges', null, 'Side Lunges', 'x10', {
    ...MODERATE, noJump: true, perSide: true, level: 'BEGINNER', focus: ['LEGS', 'BUTT'],
    primaryMuscles: ['adductors'], secondaryMuscles: ['quadriceps', 'glutes'],
    instructions: [
      'Stand with your feet together.',
      'Take a big step to the side and bend that knee, sitting your hips back. Keep the other leg straight.',
      'Push off to return to the start.',
    ],
  }),
  e('curtsy_lunges', null, 'Curtsy Lunges', 'x10', {
    ...MODERATE, noJump: true, perSide: true, level: 'INTERMEDIATE', focus: ['BUTT', 'LEGS'],
    primaryMuscles: ['glutes'], secondaryMuscles: ['quadriceps', 'adductors'],
    instructions: [
      'Stand with your feet hip-width apart.',
      'Step your right leg diagonally behind your left, as if curtsying, and bend both knees.',
      'Return to standing and repeat on the other side.',
    ],
  }),
  e('sumo_squats', null, 'Sumo Squats', 'x15', {
    ...MODERATE, noJump: true, level: 'BEGINNER', focus: ['LEGS', 'BUTT'],
    primaryMuscles: ['adductors'], secondaryMuscles: ['quadriceps', 'glutes'],
    instructions: [
      'Stand with your feet wide and your toes turned out.',
      'Bend your knees and lower your hips straight down, keeping your back upright.',
      'Squeeze your glutes to stand back up.',
    ],
  }),
  e('squat_pulses', null, 'Squat Pulses', '30s', {
    ...MODERATE, noJump: true, level: 'INTERMEDIATE', focus: ['LEGS', 'BUTT'],
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['glutes'],
    instructions: [
      'Lower into a squat.',
      'Stay low and pulse up and down a few centimetres at a time.',
      'Keep your weight in your heels and chest up.',
    ],
  }),
  e('wall_sit', null, 'Wall Sit', '30s', {
    ...MODERATE, noJump: true, level: 'BEGINNER', equipment: 'WALL', focus: ['LEGS'],
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['glutes', 'calves'],
    instructions: [
      'Stand with your back against a wall and your feet about 60 cm in front of you.',
      'Slide down the wall until your knees are bent at about 90 degrees.',
      'Hold the position, keeping your back flat against the wall.',
    ],
  }),
  e('glute_bridge', 'Butt_Lift_Bridge', 'Glute Bridge', 'x15', {
    ...CORE, focus: ['BUTT', 'LEGS'], harder: 'single_leg_bridge',
    instructions: [
      'Lie on your back with your knees bent and your feet flat, hip-width apart.',
      'Squeeze your glutes and lift your hips until your body is straight from knees to shoulders.',
      'Pause at the top, then lower slowly.',
    ],
  }),
  e('single_leg_bridge', 'Single_Leg_Glute_Bridge', 'Single-Leg Glute Bridge', 'x10', {
    ...CORE, perSide: true, level: 'INTERMEDIATE', focus: ['BUTT'], easier: 'glute_bridge',
  }),
  e('donkey_kicks', 'Glute_Kickback', 'Donkey Kicks', 'x12', {
    ...CORE, perSide: true, focus: ['BUTT'],
    instructions: [
      'Kneel on all fours with your hands under your shoulders.',
      'Keeping the knee bent, lift one leg until the thigh is level with your torso.',
      'Squeeze your glute at the top, lower, and repeat. Switch sides.',
    ],
  }),
  e('fire_hydrants', null, 'Fire Hydrants', 'x12', {
    ...CORE, perSide: true, level: 'BEGINNER', focus: ['BUTT'],
    primaryMuscles: ['abductors'], secondaryMuscles: ['glutes'],
    instructions: [
      'Kneel on all fours.',
      'Keeping your knee bent at 90 degrees, lift one leg out to the side.',
      'Lower it with control, finish the set, then switch sides.',
    ],
  }),
  e('side_leg_lifts', null, 'Side-Lying Leg Lifts', 'x12', {
    ...CORE, perSide: true, level: 'BEGINNER', focus: ['BUTT', 'LEGS'],
    primaryMuscles: ['abductors'], secondaryMuscles: ['glutes'],
    instructions: [
      'Lie on your side with your legs straight and stacked.',
      'Lift your top leg up towards the ceiling, keeping your toes pointing forward.',
      'Lower slowly. Finish the set, then switch sides.',
    ],
  }),
  e('calf_raises', null, 'Calf Raises', 'x20', {
    ...CORE, noJump: true, level: 'BEGINNER', focus: ['LEGS'],
    primaryMuscles: ['calves'], secondaryMuscles: [],
    instructions: [
      'Stand tall, holding a wall or chair for balance if you need it.',
      'Rise up onto the balls of your feet as high as you can.',
      'Lower your heels slowly back to the floor.',
    ],
  }),
  e('split_squats', null, 'Split Squats', 'x10', {
    ...MODERATE, noJump: true, perSide: true, level: 'INTERMEDIATE', focus: ['LEGS', 'BUTT'], harder: 'split_jumps',
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['glutes', 'hamstrings'],
    instructions: [
      'Stand in a long stride with one foot forward and the back heel lifted.',
      'Lower straight down until your back knee nearly touches the floor.',
      'Push through your front foot to rise. Finish the set, then switch legs.',
    ],
  }),

  // ---------------------------------------------------------------- Shoulders and back
  e('pike_push_ups', null, 'Pike Push-Ups', 'x8', {
    ...MODERATE, noJump: true, level: 'INTERMEDIATE', focus: ['SHOULDERS_BACK', 'ARMS'], harder: 'handstand_push_ups',
    primaryMuscles: ['shoulders'], secondaryMuscles: ['triceps'],
    instructions: [
      'Start in a downward-dog shape: hands and feet on the floor, hips high.',
      'Bend your elbows to lower the top of your head towards the floor.',
      'Press back up until your arms are straight.',
    ],
  }),
  e('handstand_push_ups', 'Handstand_Push-Ups', 'Wall Handstand Push-Ups', 'x5', {
    ...MODERATE, level: 'ADVANCED', equipment: 'WALL', focus: ['SHOULDERS_BACK', 'ARMS'], easier: 'pike_push_ups',
  }),
  e('superman', 'Superman', 'Superman', 'x12', {
    ...CORE, category: 'STRENGTH', focus: ['SHOULDERS_BACK'],
    instructions: [
      'Lie face down with your arms stretched out in front of you.',
      'Lift your arms, chest and legs off the floor at the same time.',
      'Hold for a second, then lower with control.',
    ],
  }),
  e('back_extensions', null, 'Floor Back Extensions', 'x12', {
    ...CORE, level: 'BEGINNER', focus: ['SHOULDERS_BACK'], harder: 'superman',
    primaryMuscles: ['lower back'], secondaryMuscles: ['glutes'],
    instructions: [
      'Lie face down with your fingertips lightly touching your temples.',
      'Keeping your feet on the floor, lift your chest a few centimetres.',
      'Hold for a moment, then lower slowly.',
    ],
  }),
  e('reverse_snow_angels', null, 'Reverse Snow Angels', 'x12', {
    ...CORE, level: 'BEGINNER', focus: ['SHOULDERS_BACK'],
    primaryMuscles: ['middle back'], secondaryMuscles: ['shoulders', 'lower back'],
    instructions: [
      'Lie face down with your arms by your sides, palms down.',
      'Lift your chest and arms slightly off the floor.',
      'Sweep your arms out and overhead in a wide arc, then back to your sides.',
    ],
  }),
  e('prone_y_raises', null, 'Prone Y Raises', 'x12', {
    ...CORE, level: 'BEGINNER', focus: ['SHOULDERS_BACK'],
    primaryMuscles: ['middle back'], secondaryMuscles: ['shoulders'],
    instructions: [
      'Lie face down with your arms overhead in a Y shape, thumbs up.',
      'Squeeze your shoulder blades and lift your arms off the floor.',
      'Hold briefly, then lower.',
    ],
  }),
  e('wall_angels', null, 'Wall Angels', 'x10', {
    ...LIGHT, category: 'STRETCH', level: 'BEGINNER', equipment: 'WALL', focus: ['SHOULDERS_BACK'],
    primaryMuscles: ['shoulders'], secondaryMuscles: ['middle back'],
    instructions: [
      'Stand with your back, head and bottom against a wall.',
      'Raise your arms into a goal-post shape with the backs of your hands on the wall.',
      'Slide your arms up overhead and back down, keeping contact with the wall.',
    ],
  }),
  e('shoulder_taps', null, 'Plank Shoulder Taps', 'x16', {
    ...CORE, level: 'INTERMEDIATE', focus: ['SHOULDERS_BACK', 'ABS'],
    primaryMuscles: ['shoulders'], secondaryMuscles: ['abdominals', 'triceps'],
    instructions: [
      'Start in a high plank with your feet a little wider than hip-width.',
      'Lift one hand and tap the opposite shoulder.',
      'Place it back down and alternate, keeping your hips still.',
    ],
  }),
  e('inverted_row', 'Inverted_Row', 'Inverted Row', 'x8', {
    ...MODERATE, level: 'INTERMEDIATE', equipment: 'BAR', focus: ['SHOULDERS_BACK', 'ARMS'],
    instructions: [
      'Lie under a sturdy table or a low bar and grip its edge with your hands shoulder-width apart.',
      'Straighten your body so only your heels touch the floor.',
      'Pull your chest up to the edge, squeezing your shoulder blades together.',
      'Lower yourself with control until your arms are straight.',
    ],
    tips: ['Test that the table can take your weight before you start.'],
  }),
  e('pull_ups', 'Pullups', 'Pull-Ups', 'x6', {
    ...MODERATE, level: 'ADVANCED', equipment: 'BAR', focus: ['SHOULDERS_BACK', 'ARMS'], easier: 'inverted_row',
  }),
  e('chin_ups', 'Chin-Up', 'Chin-Ups', 'x6', {
    ...MODERATE, level: 'ADVANCED', equipment: 'BAR', focus: ['ARMS', 'SHOULDERS_BACK'], easier: 'inverted_row',
  }),

  // ---------------------------------------------------------------- Dumbbell strength
  e('dumbbell_curl', 'Dumbbell_Bicep_Curl', 'Dumbbell Biceps Curl', 'x12', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['ARMS'],
  }),
  e('hammer_curl', 'Hammer_Curls', 'Hammer Curl', 'x12', { ...MODERATE, equipment: 'DUMBBELL', focus: ['ARMS'] }),
  e('dumbbell_kickback', 'Tricep_Dumbbell_Kickback', 'Triceps Kickback', 'x12', {
    ...MODERATE, perSide: true, equipment: 'DUMBBELL', focus: ['ARMS'],
  }),
  e('dumbbell_shoulder_press', 'Dumbbell_Shoulder_Press', 'Dumbbell Shoulder Press', 'x10', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['SHOULDERS_BACK', 'ARMS'],
    instructions: [
      'Stand or sit tall with a dumbbell in each hand at shoulder height, palms facing forward.',
      'Press the weights straight up until your arms are extended overhead.',
      'Lower them slowly back to shoulder height.',
    ],
  }),
  e('lateral_raise', 'Side_Lateral_Raise', 'Lateral Raise', 'x12', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['SHOULDERS_BACK'],
  }),
  e('dumbbell_row', 'Bent_Over_Two-Dumbbell_Row', 'Bent-Over Dumbbell Row', 'x12', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['SHOULDERS_BACK', 'ARMS'],
  }),
  e('dumbbell_squat', 'Dumbbell_Squat', 'Dumbbell Squat', 'x12', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['LEGS', 'BUTT'],
  }),
  e('dumbbell_lunges', 'Dumbbell_Lunges', 'Dumbbell Lunges', 'x10', {
    ...MODERATE, perSide: true, equipment: 'DUMBBELL', focus: ['LEGS', 'BUTT'],
  }),
  e('dumbbell_floor_press', 'Dumbbell_Floor_Press', 'Dumbbell Floor Press', 'x10', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['CHEST', 'ARMS'],
  }),
  e('reverse_flyes', 'Reverse_Flyes', 'Reverse Fly', 'x12', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['SHOULDERS_BACK'],
    instructions: [
      'Hold a light dumbbell in each hand and hinge forward at the hips with a flat back.',
      'Let the weights hang below your chest, palms facing each other.',
      'With a slight bend in your elbows, raise your arms out to the sides to shoulder height.',
      'Lower them slowly.',
    ],
  }),
  e('arnold_press', 'Arnold_Dumbbell_Press', 'Arnold Press', 'x10', {
    ...MODERATE, level: 'INTERMEDIATE', equipment: 'DUMBBELL', focus: ['SHOULDERS_BACK'],
  }),
  e('dumbbell_calf_raise', 'Standing_Dumbbell_Calf_Raise', 'Dumbbell Calf Raise', 'x15', {
    ...CORE, equipment: 'DUMBBELL', focus: ['LEGS'],
  }),
  e('plie_squat', 'Plie_Dumbbell_Squat', 'Dumbbell Plié Squat', 'x12', {
    ...MODERATE, equipment: 'DUMBBELL', focus: ['LEGS', 'BUTT'],
  }),

  // ---------------------------------------------------------------- Stretching and mobility
  e('cat_cow', 'Cat_Stretch', 'Cat-Cow', '30s', {
    ...STRETCH, focus: ['SHOULDERS_BACK'],
    instructions: [
      'Kneel on all fours with a flat back.',
      'Breathe in as you drop your belly and lift your chest and tailbone.',
      'Breathe out as you round your spine towards the ceiling and tuck your chin.',
      'Flow slowly between the two.',
    ],
  }),
  e('childs_pose', 'Childs_Pose', "Child's Pose", '30s', {
    ...STRETCH, focus: ['SHOULDERS_BACK'],
    instructions: [
      'Kneel and sit back on your heels with your knees apart.',
      'Fold forward and stretch your arms out in front of you on the floor.',
      'Rest your forehead down and breathe slowly.',
    ],
  }),
  e('cobra_stretch', null, 'Cobra Stretch', '30s', {
    ...STRETCH, level: 'BEGINNER', focus: ['ABS', 'SHOULDERS_BACK'],
    primaryMuscles: ['abdominals'], secondaryMuscles: ['lower back'],
    instructions: [
      'Lie face down with your hands under your shoulders.',
      'Gently press your chest up, keeping your hips on the floor.',
      'Relax your shoulders down and hold, breathing slowly.',
    ],
  }),
  e('hamstring_stretch', 'Leg-Up_Hamstring_Stretch', 'Hamstring Stretch', '30s', { ...STRETCH, perSide: true, focus: ['LEGS'] }),
  e('quad_stretch', 'On_Your_Side_Quad_Stretch', 'Quad Stretch', '30s', { ...STRETCH, perSide: true, focus: ['LEGS'] }),
  e('calf_stretch', 'Calf_Stretch_Hands_Against_Wall', 'Calf Stretch', '30s', {
    ...STRETCH, perSide: true, equipment: 'WALL', focus: ['LEGS'],
  }),
  e('hip_flexor_stretch', 'Kneeling_Hip_Flexor', 'Kneeling Hip Flexor Stretch', '30s', {
    ...STRETCH, perSide: true, focus: ['LEGS', 'BUTT'],
  }),
  e('butterfly_stretch', null, 'Butterfly Stretch', '30s', {
    ...STRETCH, level: 'BEGINNER', focus: ['LEGS'],
    primaryMuscles: ['adductors'], secondaryMuscles: ['glutes'],
    instructions: [
      'Sit tall with the soles of your feet together and your knees falling out to the sides.',
      'Hold your feet and gently lean forward from your hips.',
      'Hold, letting your knees relax towards the floor.',
    ],
  }),
  e('knees_to_chest', 'Hug_Knees_To_Chest', 'Knees to Chest', '30s', { ...STRETCH, focus: ['SHOULDERS_BACK'] }),
  e('glute_stretch', null, 'Figure-4 Glute Stretch', '30s', {
    ...STRETCH, perSide: true, level: 'BEGINNER', focus: ['BUTT'],
    primaryMuscles: ['glutes'], secondaryMuscles: ['hamstrings'],
    instructions: [
      'Lie on your back with your knees bent and feet flat.',
      'Cross your right ankle over your left knee.',
      'Lift your left foot and pull your left thigh towards your chest until you feel the stretch.',
      'Hold, then switch sides.',
    ],
  }),
  e('spinal_twist', 'Lying_Crossover', 'Lying Spinal Twist', '30s', {
    ...STRETCH, perSide: true, focus: ['SHOULDERS_BACK'],
    instructions: [
      'Lie on your back with your arms out to the sides.',
      'Bring one knee up and let it drop across your body towards the floor.',
      'Keep both shoulders down and look the other way. Hold, then switch sides.',
    ],
  }),
  e('shoulder_stretch', 'Shoulder_Stretch', 'Shoulder Stretch', '20s', { ...STRETCH, perSide: true, focus: ['SHOULDERS_BACK'] }),
  e('triceps_stretch', 'Triceps_Stretch', 'Triceps Stretch', '20s', { ...STRETCH, perSide: true, focus: ['ARMS'] }),
  e('chest_opener', 'Elbows_Back', 'Chest Opener', '30s', { ...STRETCH, focus: ['CHEST'] }),
  e('side_neck_stretch', 'Side_Neck_Stretch', 'Side Neck Stretch', '20s', { ...STRETCH, perSide: true, focus: ['SHOULDERS_BACK'] }),
  e('chin_to_chest', 'Chin_To_Chest_Stretch', 'Chin to Chest Stretch', '20s', { ...STRETCH, focus: ['SHOULDERS_BACK'] }),
  e('standing_side_stretch', 'Standing_Lateral_Stretch', 'Standing Side Stretch', '20s', { ...STRETCH, perSide: true, focus: ['ABS'] }),
  e('worlds_greatest_stretch', 'Worlds_Greatest_Stretch', "World's Greatest Stretch", 'x5', {
    ...STRETCH, perSide: true, focus: ['FULL_BODY'],
  }),
  e('upper_back_stretch', 'Upper_Back_Stretch', 'Upper Back Stretch', '30s', { ...STRETCH, focus: ['SHOULDERS_BACK'] }),
  e('overhead_stretch', 'Overhead_Stretch', 'Overhead Reach', '30s', { ...STRETCH, focus: ['ABS', 'FULL_BODY'] }),
  e('wrist_circles', 'Wrist_Circles', 'Wrist Circles', '20s', { ...STRETCH, focus: ['ARMS'] }),
  e('ankle_circles', 'Ankle_Circles', 'Ankle Circles', '20s', {
    ...STRETCH, focus: ['LEGS'],
    instructions: [
      'Stand on one leg, holding a wall or chair for balance.',
      'Lift the other foot slightly and draw slow circles with your toes.',
      'Halfway through, reverse direction, then switch feet.',
    ],
  }),
  e('hip_circles', 'Standing_Hip_Circles', 'Hip Circles', '30s', { ...STRETCH, focus: ['BUTT', 'LEGS'] }),
  e('shoulder_circles', 'Shoulder_Circles', 'Shoulder Rolls', '20s', { ...STRETCH, focus: ['SHOULDERS_BACK'] }),
  e('toe_touches', 'Standing_Toe_Touches', 'Standing Toe Touches', '30s', { ...STRETCH, focus: ['LEGS'] }),
  e('pelvic_tilt_bridge', 'Pelvic_Tilt_Into_Bridge', 'Pelvic Tilt into Bridge', 'x10', {
    ...STRETCH, focus: ['SHOULDERS_BACK', 'BUTT'],
  }),
  e('knee_circles', 'Knee_Circles', 'Knee Circles', '20s', { ...STRETCH, focus: ['LEGS'] }),
  e('seated_hamstring_stretch', 'Seated_Floor_Hamstring_Stretch', 'Seated Hamstring Stretch', '30s', {
    ...STRETCH, focus: ['LEGS'],
  }),
  e('chair_back_stretch', 'Chair_Lower_Back_Stretch', 'Seated Back Stretch', '30s', {
    ...STRETCH, equipment: 'CHAIR', focus: ['SHOULDERS_BACK'],
  }),
  e('seated_glute_stretch', 'Seated_Glute', 'Seated Glute Stretch', '30s', {
    ...STRETCH, perSide: true, equipment: 'CHAIR', focus: ['BUTT'],
    instructions: [
      'Sit tall on a chair and cross one ankle over the opposite knee.',
      'Keeping your back straight, lean forward from your hips until you feel the stretch.',
      'Hold, then switch sides.',
    ],
  }),
  e('windmills', 'Windmills', 'Windmills', '30s', { ...STRETCH, focus: ['FULL_BODY'] }),
  e('dynamic_back_stretch', 'Dynamic_Back_Stretch', 'Dynamic Back Stretch', '30s', { ...STRETCH, focus: ['SHOULDERS_BACK'] }),
  e('standing_hip_flexor', 'Standing_Hip_Flexors', 'Standing Hip Flexor Stretch', '30s', {
    ...STRETCH, perSide: true, focus: ['LEGS'],
  }),
  e('leg_swings', 'Front_Leg_Raises', 'Front Leg Swings', 'x10', { ...STRETCH, perSide: true, focus: ['LEGS'] }),
  e('middle_back_stretch', 'Middle_Back_Stretch', 'Middle Back Stretch', '30s', { ...STRETCH, focus: ['SHOULDERS_BACK'] }),
  e('neck_isometrics', 'Isometric_Neck_Exercise_-_Sides', 'Neck Side Press', '20s', {
    ...STRETCH, category: 'STRENGTH', perSide: true, focus: ['SHOULDERS_BACK'],
  }),

  // ---------------------------------------------------------------- Recovery and chair
  e('clamshells', null, 'Clamshells', 'x12', {
    ...CORE, perSide: true, level: 'BEGINNER', focus: ['BUTT'],
    primaryMuscles: ['abductors'], secondaryMuscles: ['glutes'],
    instructions: [
      'Lie on your side with your knees bent and feet together.',
      'Keeping your feet touching, lift your top knee as far as is comfortable.',
      'Lower slowly. Finish the set, then switch sides.',
    ],
  }),
  e('heel_slides', null, 'Heel Slides', 'x10', {
    ...STRETCH, perSide: true, level: 'BEGINNER', focus: ['LEGS'],
    primaryMuscles: ['hamstrings'], secondaryMuscles: ['quadriceps'],
    instructions: [
      'Lie on your back with both legs straight.',
      'Slowly slide one heel towards your bottom, bending the knee as far as is comfortable.',
      'Slide it back out straight. Finish the set, then switch legs.',
    ],
    tips: ['Move within a pain-free range; this is about mobility, not effort.'],
  }),
  e('straight_leg_raise', null, 'Straight Leg Raise', 'x10', {
    ...CORE, perSide: true, level: 'BEGINNER', focus: ['LEGS'],
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['abdominals'],
    instructions: [
      'Lie on your back with one knee bent and the other leg straight.',
      'Tighten the thigh of the straight leg and lift it to the height of the bent knee.',
      'Lower slowly. Finish the set, then switch legs.',
    ],
  }),
  e('seated_knee_extension', null, 'Seated Knee Extension', 'x12', {
    ...LIGHT, category: 'STRENGTH', perSide: true, level: 'BEGINNER', equipment: 'CHAIR', focus: ['LEGS'],
    primaryMuscles: ['quadriceps'], secondaryMuscles: [],
    instructions: [
      'Sit tall on a chair with your feet flat on the floor.',
      'Straighten one knee until the leg is level with the seat.',
      'Hold for a second, then lower slowly. Finish the set, then switch legs.',
    ],
  }),
  e('seated_march', null, 'Seated March', '30s', {
    ...LIGHT, met: 2.5, level: 'BEGINNER', equipment: 'CHAIR', focus: ['LEGS', 'FULL_BODY'],
    primaryMuscles: ['quadriceps'], secondaryMuscles: ['abdominals'],
    instructions: [
      'Sit tall near the front of a chair.',
      'Lift one knee as high as is comfortable, lower it, then lift the other.',
      'Pump your arms as you march.',
    ],
  }),
  e('seated_torso_twist', null, 'Seated Torso Twist', '30s', {
    ...STRETCH, level: 'BEGINNER', equipment: 'CHAIR', focus: ['ABS'],
    primaryMuscles: ['abdominals'], secondaryMuscles: ['lower back'],
    instructions: [
      'Sit tall on a chair with your feet flat and arms crossed over your chest.',
      'Rotate your upper body to one side as far as is comfortable.',
      'Return to the centre and rotate to the other side.',
    ],
  }),
  e('seated_punches', null, 'Seated Punches', '30s', {
    ...LIGHT, met: 2.8, level: 'BEGINNER', equipment: 'CHAIR', focus: ['ARMS'],
    primaryMuscles: ['shoulders'], secondaryMuscles: ['triceps'],
    instructions: [
      'Sit tall near the front of a chair with your fists by your chin.',
      'Punch forward with one arm, then the other, rotating slightly through your torso.',
      'Keep a quick, steady pace.',
    ],
  }),
];
