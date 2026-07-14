# Extracted agent prompts (reference workbook)

Source: `ref/Agent/agent_prompts.xlsx` — extracted verbatim on 2026-07-14.
These are the working base for CereBroZen prompt content (owner decision,
2026-07-14); adapt per `docs/PROMPTS_SPEC.md` (voice, contracts, length budgets).

| Sheet | Enabled | Model | Chars | Fragments | File |
|---|---|---|---|---|---|
| CH_coaching_agent | True | gpt-5.4 | 70,904 | 3 | [ch_coaching_agent.md](ch_coaching_agent.md) |
| environment_system_agent | ? | ? | 45,014 | 2 | [environment_system_agent.md](environment_system_agent.md) |
| core_coaching_agent | TRUE | gpt-5.4 | 39,018 | 2 | [core_coaching_agent.md](core_coaching_agent.md) |
| coaching_intake_agent | TRUE | gpt-5.4 | 27,830 | 1 | [coaching_intake_agent.md](coaching_intake_agent.md) |
| orchestrator | TRUE | gpt-5.4 | 27,375 | 1 | [orchestrator.md](orchestrator.md) |
| user_profile_retrieval_agent | TRUE | gpt-5.4 | 26,779 | 1 | [user_profile_retrieval_agent.md](user_profile_retrieval_agent.md) |
| user_context_builder_agent | TRUE | gpt-5.4 | 26,191 | 1 | [user_context_builder_agent.md](user_context_builder_agent.md) |
| challenge_context_agent | TRUE | gpt-5.4 | 24,135 | 1 | [challenge_context_agent.md](challenge_context_agent.md) |
| pattern_agent | True | gpt-5-mini | 21,690 | 1 | [pattern_agent.md](pattern_agent.md) |
| role_play_agent | TRUE | gpt-5.4 | 20,338 | 1 | [role_play_agent.md](role_play_agent.md) |
| feedback_mood_capture_agent | True | gpt-5-mini | 17,610 | 1 | [feedback_mood_capture_agent.md](feedback_mood_capture_agent.md) |
| learning_aid_agent | True | gpt-5.4 | 16,726 | 1 | [learning_aid_agent.md](learning_aid_agent.md) |
| SJT_simulation_agent | TRUE | gpt-5.4 | 14,921 | 1 | [sjt_simulation_agent.md](sjt_simulation_agent.md) |
| simulation_decision_agent | TRUE | gpt-5.4 | 13,824 | 1 | [simulation_decision_agent.md](simulation_decision_agent.md) |
| dynamic_actions_insights_agent | True | gpt-5.4 | 12,892 | 1 | [dynamic_actions_insights_agent.md](dynamic_actions_insights_agent.md) |
| action_checkin_agent | True | gpt-5-mini | 10,840 | 1 | [action_checkin_agent.md](action_checkin_agent.md) |
| repeat_user_checkin_agent | True | gpt-5-mini | 2,506 | 1 | [repeat_user_checkin_agent.md](repeat_user_checkin_agent.md) |
| placeholder_replacement_agent | False | gpt-5-nano | 1,421 | 1 | [placeholder_replacement_agent.md](placeholder_replacement_agent.md) |

Config tabs: [_catalog.md](_catalog.md) · [_extraction.md](_extraction.md) · [_dynamic_variables_persistent.md](_dynamic_variables_persistent.md)
