"""Voice transport (Phase 10) — LiveKit cascade worker.

This package is the "mouth + ears" around the existing LangGraph brain:
Deepgram STT -> the brain (app.service) -> ElevenLabs TTS, with barge-in handled
by LiveKit's VAD. The brain is unchanged — voice is just another transport over
the same session/checkpoint as text.
"""
