# Local AI model evaluation — Android V1

## Primary candidate: distilled LFM2.5 QAD
- Hugging Face repository: https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF
- File: `LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf` (about 696 MB).
- Revision pinned for download: `430bea1` (verified model-file page revision).
- SHA-256: `bb741ebb106d543e9de114b843a3d3d73d51c74b5801e69da2abde821a0cb3e1`.
- Training description: Liquid AI describes QAD as a high-precision teacher distilled into a quantized student; reported results recover 97% of the BF16 average accuracy lost to quantization. This is the teacher/student-style checkpoint requested.
- Runtime: upstream llama.cpp Android JNI/NDK. The model remains a separate user-downloaded artifact; it is not bundled into the APK.
- License: LFM Open License v1.0. It permits free commercial use below the USD 10 million annual-revenue threshold and research/nonprofit use without that threshold. Include the license and required attribution if redistributing the model.
- Source model card lists 8 languages (English, Arabic, Chinese, French, German, Japanese, Korean, Spanish); Urdu is not listed. Urdu/Roman Urdu must therefore be tested rather than assumed.

## Secondary candidate: Qwen3.5 2B
- Hugging Face conversion: https://huggingface.co/SoAIHQ/Qwen3.5-2B-GGUF
- File: `Qwen3.5-2B-Q4_K_M.gguf` (about 1.3 GB).
- Original model license: Apache 2.0; conversion provenance and checksum are documented by the publisher.
- This is a larger fallback candidate, not a teacher/student-distilled checkpoint.

## Runtime safety
- Do not download model files until the user explicitly starts the in-app model download and the app has enough free storage.
- Verify the complete SHA-256 before marking a model installed or loading it.
- Start with a 2,048-token context and conservative CPU thread count; tune after measurements.
- Model outputs are drafts, not verified facts. It is not authoritative for visa, work-permit, credential or hiring decisions.
- No CV, passport or personal document is sent to a cloud model or used to train model weights.

## Device acceptance tests
- Measure load time, peak RAM, first-token latency, tokens/second, battery and temperature on the target Android phone.
- Test English, Urdu, Roman Urdu and mixed-language prompts.
- Test structured extraction against synthetic CV/job fixtures.
- Reject invented qualifications, employers, dates, licenses and visa eligibility.
- Keep Qwen as an optional fallback only if the smaller model fails the language/quality gate and the phone can sustain it.
