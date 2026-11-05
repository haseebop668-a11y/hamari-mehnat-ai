# Local AI model evaluation — Android V1

## Primary candidate
- Base model: Qwen/Qwen3.5-2B-Instruct.
- Runtime artifact: GGUF Q4_K_M, from a provenance-documented Hugging Face conversion pinned to an immutable revision after checksum verification.
- License direction: Apache 2.0 at the original model level; verify the exact conversion repository and included files before shipping.
- Runtime: upstream llama.cpp Android integration through JNI/NDK.
- Start with a 2,048–4,096 token context and tune using measured memory.

## Teacher/student or distilled alternative
- Candidate: LiquidAI/LFM2.5-1.2B-Instruct-GGUF, including its QAD-Q4_0 checkpoint (about 696 MB).
- The model card identifies QAD as Quantization-Aware Distillation. This is a genuine distillation-related candidate to test, not an assumption based on model size.
- The LFM2.5 model uses the LFM 1.0 license, not Apache 2.0. Review the exact license terms before distributing the model inside an APK. The model can remain an optional user-downloaded artifact if the terms permit the intended use.
- Do not call QAD identical to conventional teacher/student fine-tuning without reviewing the training recipe.

## Why these candidates
A 1.2B–2B instruction model is a practical footprint range for CV summaries, job-description explanation, interview practice and drafting. The model is not authoritative for visa, work-permit or eligibility decisions. Deterministic matching and official sources remain authoritative.

## Device acceptance tests
- Measure actual free RAM, load time, peak memory, first-token latency, tokens/second, battery and temperature.
- Test English, Urdu, Roman Urdu and mixed-language prompts.
- Test structured extraction against synthetic CV/job fixtures.
- Reject invented qualifications, employers, dates, licenses and visa eligibility.
- Keep a smaller fallback if the 2B candidate is unstable or too slow.

## Safety
The model drafts and explains; it does not submit applications or send messages. User documents are retrieval context only and will not be used to train or fine-tune model weights. All user-facing facts must be traceable to reviewed profile fields or cited source URLs.
