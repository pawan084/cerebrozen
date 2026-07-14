# workbook tab: dynamic_variables_persistent

| variable_name | update_frequency | capture_enabled | source_agent | prompt_placeholder | exact_location | notes |  |  |
|---|---|---|---|---|---|---|---|---|
| ci_openness | once_in_lifetime | True | coaching_intake_agent | {ci_openness}  | Q1 — Openness  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_accountability | once_in_lifetime | True | coaching_intake_agent | {ci_accountability}  | Q1 — Accountability  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_growth_mindset | once_in_lifetime | True | coaching_intake_agent | {ci_growth_mindset}  | Q1 — Growth Mindset  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_action_bias | once_in_lifetime | True | coaching_intake_agent | {ci_action_bias}  | Q1 — Action Bias  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_honesty | once_in_lifetime | True | coaching_intake_agent | {ci_honesty}  | Q1 — Honesty  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_consistency | once_in_lifetime | True | coaching_intake_agent | {ci_consistency}  | Q1 — Consistency  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_specificity | once_in_lifetime | True | coaching_intake_agent | {ci_specificity}  | Q1 — Specificity  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| ci_reflectiveness | once_in_lifetime | True | coaching_intake_agent | {ci_reflectiveness}  | Q1 — Reflectiveness  | Internal only. Never surfaced to user. Set once, not updated on return. |  |  |
| coachability_score | once_in_lifetime | True | coaching_intake_agent | {coachability_score} | Q1 - Coachable Index (8-dimension assessment) | Internal only. Never surfaced to user. Set once, not updated on return. |  |   |
| userRoleContext | once_in_lifetime | True | coaching_intake_agent | {userRoleContext} | Q2 - Role/work experience question | Set once. Never updated, even on intake re-run. |  |  |
| coachingHistory | once_in_lifetime | True | coaching_intake_agent | {coachingHistory} | Q3 - Prior coaching experience question | Set once. Never updated. |  |  |
| coachingNeeds | once_in_lifetime | True | coaching_intake_agent | {coachingNeeds} | Q4 - Same question as coachingHistory (dual capture) | Set once. Never updated. |  |  |
| coaching_style_preference | once_in_lifetime | True | coaching_intake_agent | {coaching_style_preference} | Q5 - Coaching style preference question | Set once. NEVER updated. Distinct from coaching_style_context.selected_style. |  |  |
| userMotivations | once_in_lifetime | True | coaching_intake_agent | {userMotivations} | Q6 - Motivations question | Set once. Never updated. |  |  |
| coaching_style_context.selected_style | every_session | True | challenge_context_agent | {coaching_style_context} | Step 8a - Coaching style question (fresh/repeat variant) | Per-session signal. NOT the same as coaching_style_preference. Dot-path nests under coaching_style_context. |  |  |
| confirmed_competency | every_session | True | CH_coaching_agent | {confirmed_competency} | Phase 1 - Competency confirmation (after Use Case Detection Step 1) | Sacred. Locked in Phase 1. Never re-selected or re-confirmed in Phases 2 or 3. |  |  |
| user_blueprint | every_session | True | CH_coaching_agent | {user_blueprint} | Phase 1, Step 24 - Development Blueprint drawn at end of Phase 1 | Updated at end of Phase 2. Source of truth within CH agent. |  |  |
| mastery_rubric | every_session | True | CH_coaching_agent | {mastery_rubric} | Phase 1, Step 21 - Mastery definition question | Carries into Phase 3 Step 8 as Advanced benchmark. |  |  |
| long_term_goal | every_session | True | CH_coaching_agent | {long_term_goal} | Phase 2, Steps 16-17 - Goal setting steps | Stored inside user_blueprint. |  |  |
| user_career_aspirations | every_session | True | CH_coaching_agent | {user_career_aspirations} | Phase 1 - Aspiration/outcome questioning | Stored inside user_blueprint. |  |  |
| user_strengths.self_reported | every_session | True | CH_coaching_agent | {user_strengths} | Phase 1, Step 7 - Strengths self-reported question | null for CIM/CBT-only users. Dot-path nests under user_strengths. |  |  |
| user_strengths.seen_by_others | every_session | True | CH_coaching_agent | {user_strengths} | Phase 1, Step 7 - Same step as self_reported | null for CIM/CBT-only users. |  |  |
| user_gaps | every_session | True | CH_coaching_agent | {user_gaps} | Phase 1, Step 7 - Development gaps (same step as strengths) | null for CIM/CBT-only users. |  |  |
| user_work_environment.geographical_context | every_session | True | CH_coaching_agent | {user_work_environment} | Phase 2, Steps 9-11 - Culture/environment questions | Sub-field of spec var #16. null for CIM/CBT-only users. |  |  |
| user_work_environment.feedback_style | every_session | True | CH_coaching_agent | {user_work_environment} | Phase 2, Steps 9-11 - Culture/environment questions | Sub-field of spec var #16. null for CIM/CBT-only users. |  |  |
| user_work_environment.values_which_resonate | every_session | True | CH_coaching_agent | {user_work_environment} | Phase 2, Steps 9-11 - Culture/environment questions | Sub-field of spec var #16. null for CIM/CBT-only users. |  |  |
| previousUserContext | every_session | False | user_context_builder_agent | {previousUserContext} | Post-session write - after feedback_mood_capture_agent completes | capture_enabled=FALSE: written by save_user_context_model(), not via variables_set. |  |  |
| session_count | every_session | False | user_context_builder_agent | {session_count} | Post-session write - sessions_completed increments by 1 each run | capture_enabled=FALSE: system-incremented, not via variables_set. |  |  |
| last_session_at | every_session | False | user_context_builder_agent | {last_session_at} | Post-session write - timestamp stamped at session close | capture_enabled=FALSE: written by save_user_context_model() at close. |  |  |
| behavioral_intake_responses | only_on_shift | True | core_coaching_agent | {behavioral_intake_responses} | Stage 0 - Dominant workday behaviour question | Only updated if a behavioral shift is observed. Agent controls when to emit in variables_set. |  |  |
