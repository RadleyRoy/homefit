// Where each exercise's photos and each workout cover come from, kept apart from catalog.mjs
// so the exercise definitions stay about exercises. import-exercises.mjs merges this in.
//
// Every exercise photo shows a person doing that move. Sources, all free to bundle:
//   - free-exercise-db (Unlicense / public domain): the default for entries with a `src`
//   - Wikimedia Commons: public domain or CC BY / CC BY-SA; author and licence are read from
//     Commons at import time and written to credits.json, which the About screen lists
//   - Pexels (Pexels License: free to use and modify, no attribution required; credited anyway)
//
// Exercise entry forms:
//   fedb('Id', [frames])   photos from another free-exercise-db exercise (optionally a subset)
//   same('exercise_id')    reuse another catalog exercise's photos (same body position)
//   [pexels(..), commons(..)]  external photos, played in order like the two-frame animation
//
// Exercises absent from both here and free-exercise-db have no freely licensed photo yet; the
// app shows a "watch how" panel linking to a YouTube search instead.

const pexels = (id) => ({ kind: 'pexels', id });
const commons = (file) => ({ kind: 'commons', file });
const fedb = (id, frames) => ({ kind: 'fedb', id, frames });
const same = (id) => ({ kind: 'same', id });

export const exercisePhotos = {
  // Replacements for free-exercise-db photos that showed a partner, a band, or were unusable.
  spinal_twist: fedb('Knee_Across_The_Body'),
  seated_glute_stretch: [commons('Exercise Buttocks Stretch.png')],
  towel_triceps_extension: [commons('Resistance training develops functional strength (9834695).jpg')],
  wide_push_ups: [pexels(5149165)],

  // Moves that had no photo.
  jumping_jacks: [pexels(7298411)],
  high_knees: [pexels(4194670)],
  burpees: [
    commons('Burpee 1 Neutral Position.jpg'), commons('Burpee 3 Hands Grounded.jpg'),
    commons('Burpee 5 Thrust.jpg'), commons('Burpee 7 Land.jpg'),
  ],
  squat_thrusts: [commons('Burpee 3 Hands Grounded.jpg'), commons('Burpee 5 Thrust.jpg')],
  shadow_boxing: [pexels(6295832)],
  march_in_place: [pexels(4662354)],
  plank_jacks: [commons('Girl exercising doing a plank.jpg')],
  plank_up_downs: [pexels(6303467)],
  brisk_walk: [commons('Power Walking.jpg')],
  easy_run: [commons('Running in a park in Brussels - 2023 - P060598-679654.jpg')],
  fast_run: [commons('Kortnei Johnson runner.jpg')],
  leg_raises: [commons('Zirshekam1.jpg'), commons('Zirshekam3.jpg')],
  flutter_kicks: same('scissor_kicks'),
  bird_dog: [commons('Girl doing bird dog yoga pose.jpg'), commons('Girl doing bird dog yoga pose 2.jpg')],
  wall_push_ups: [pexels(5036902)],
  floor_triceps_dips: [commons('Girl doing dips Exercise.jpg')],
  arm_raises: [pexels(8523561)],
  side_arm_raises: [commons('Girl doing lateral raises with bands.jpg')],
  biceps_stretch: fedb('Standing_Biceps_Stretch'),
  chair_squats: [commons('Exercise Chair Squat.png')],
  reverse_lunges: [commons('Girl doing lunges.jpg')],
  side_lunges: [pexels(7319735)],
  curtsy_lunges: fedb('Crossover_Reverse_Lunge'),
  sumo_squats: [pexels(30246176)],
  squat_pulses: same('squats'),
  wall_sit: [pexels(6740054)],
  side_leg_lifts: [commons('Side Leg Raise.jpg')],
  calf_raises: [commons('Exercise Heel Raise Two Legs.png')],
  split_squats: fedb('Split_Squats'),
  pike_push_ups: [pexels(6303735)],
  shoulder_taps: [commons('10th AAMDC host “Key Leader Physical Readiness Training” (8448512).jpg')],
  cobra_stretch: [commons('Bhujangasana zamkovoi.jpg')],
  butterfly_stretch: fedb('Groin_and_Back_Stretch'),
  glute_stretch: fedb('Ankle_On_The_Knee'),
  heel_slides: [commons('Exercise Towel Slides.png')],
  straight_leg_raise: [commons('Exercise Straight Leg Raises.png')],
  seated_knee_extension: [commons('Straight Leg Raise Exercise.jpeg')],
  seated_torso_twist: [pexels(4534688)],
};

// Card and header photos. The app looks for covers/<workout or program id>.jpg first, then
// covers/focus_<AREA>.jpg, then covers/category_<CATEGORY>.jpg.
export const covers = {
  focus_ABS: pexels(12966655),
  focus_CHEST: pexels(4775232),
  focus_ARMS: pexels(6456020),
  focus_LEGS: pexels(4662340),
  focus_BUTT: pexels(4587402),
  focus_SHOULDERS_BACK: pexels(13506202),
  focus_FULL_BODY: pexels(23224738),

  category_WARMUP: pexels(6339477),
  category_STRETCH: pexels(4775187),
  category_HIIT: pexels(4164465),
  category_QUICK: pexels(6388391),
  category_LOW_IMPACT: pexels(1556710),
  category_CHAIR: pexels(11674389),
  category_DUMBBELL: pexels(8033049),
  category_RUNNING: commons('Running in a park in Brussels - 2023 - P060598-845611.jpg'),

  special_six_pack: pexels(20400636),
  special_bigger_arms: pexels(13318267),
  special_booty: pexels(8032914),
  special_upper_burn: pexels(4162491),

  full_body_28: pexels(4853085),
  lower_body_28: pexels(8401808),
  abs_28: pexels(30672396),
  plank_30: pexels(9376270),
  pushup_30: pexels(4803733),
  recovery_back: pexels(17572134),
  recovery_knee: pexels(8084766),
  recovery_neck: pexels(9486637),
};
