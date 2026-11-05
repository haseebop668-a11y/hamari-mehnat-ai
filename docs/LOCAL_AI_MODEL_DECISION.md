# Local AI model evaluation — Android V1

## First model to benchmark
- Candidate: Qwen3.5-2B Instruct.
- Runtime artifact: GGUF Q4_K_M, from a provenance-documented Hugging Face conversion pinned to an immutable revision after checksum verification.
- Runtime: upstream llama.cpp Android integration through JNI/NDK.
- Start with a 2,048–4,096 token context and tune using measured memory.

## Why
A 2B instruction model is a practical quality/footprint candidate for CV summaries, job-description explanation, interview practice and drafting. It must not make authoritative visa, work-permit or eligibility decisions. Deterministic matching and official sources remain authoritative.

## Teacher/student and distillation
Do not label a model teacher-student trained without checking its model card, training recipe and license. Quantization is not distillation. A QAD/distilled checkpoint may be benchmarked as a separate candidate only after provenance and license review. Private identity documents will not be used to train or fine-tune the model.

## Device acceptance tests
- Measure actual free RAM, load time, peak memory, first-token latency, tokens/second, battery and temperature.
- Test English, Urdu, Roman Urdu and mixed-language prompts.
- Test structured extraction against synthetic CV/job fixtures.
- Reject invented qualifications, employers, dates, licenses and visa eligibility.
- Keep a smaller fallback model if the 2B candidate is unstable or too slow.

## Safety
The model drafts and explains; it does not submit applications or send messages. All user-facing facts must be traceable to reviewed profile fields or cited source URLs.