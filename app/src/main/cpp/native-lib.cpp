#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "HamariLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
const llama_vocab * g_vocab = nullptr;
bool g_backend_initialized = false;

void release_model() {
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

std::string format_prompt(const std::string & prompt) {
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    if (tmpl == nullptr) {
        return "User: " + prompt + "\nAssistant:";
    }
    llama_chat_message message = {"user", prompt.c_str()};
    std::vector<char> buffer(8192);
    int32_t needed = llama_chat_apply_template(tmpl, &message, 1, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (needed < 0) {
        return "User: " + prompt + "\nAssistant:";
    }
    if (needed > static_cast<int32_t>(buffer.size())) {
        buffer.resize(static_cast<size_t>(needed) + 1);
        needed = llama_chat_apply_template(tmpl, &message, 1, true, buffer.data(), static_cast<int32_t>(buffer.size()));
        if (needed < 0 || needed > static_cast<int32_t>(buffer.size())) {
            return "User: " + prompt + "\nAssistant:";
        }
    }
    return std::string(buffer.data(), static_cast<size_t>(needed));
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hamari_mehenat_jobhunter_ai_NativeLlama_loadModel(
        JNIEnv * env, jobject, jstring model_path, jint context_size, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return JNI_FALSE;

    release_model();
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);
    if (g_model == nullptr) {
        LOGE("Failed to load GGUF model");
        return JNI_FALSE;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::clamp(context_size, 1024, 8192));
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = std::clamp(threads, 1, 6);
    context_params.n_threads_batch = std::clamp(threads, 1, 6);
    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        LOGE("Failed to create inference context");
        release_model();
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);
    return g_vocab != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hamari_mehenat_jobhunter_ai_NativeLlama_generate(
        JNIEnv * env, jobject, jstring prompt_text, jint max_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr || g_vocab == nullptr) {
        return env->NewStringUTF("Local model is not loaded.");
    }

    const char * raw_prompt = env->GetStringUTFChars(prompt_text, nullptr);
    if (raw_prompt == nullptr) return env->NewStringUTF("Could not read prompt.");
    const std::string prompt(raw_prompt);
    env->ReleaseStringUTFChars(prompt_text, raw_prompt);

    const std::string formatted = format_prompt(prompt);
    std::vector<llama_token> tokens(4096);
    int32_t token_count = llama_tokenize(
        g_vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true
    );
    if (token_count < 0) {
        tokens.resize(static_cast<size_t>(-token_count));
        token_count = llama_tokenize(
            g_vocab, formatted.c_str(), static_cast<int32_t>(formatted.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true
        );
    }
    if (token_count <= 0 || token_count > static_cast<int32_t>(tokens.size())) {
        return env->NewStringUTF("Prompt tokenization failed or exceeded context.");
    }
    tokens.resize(static_cast<size_t>(token_count));

    llama_batch batch = llama_batch_get_one(tokens.data(), token_count);
    if (llama_decode(g_context, batch) != 0) {
        return env->NewStringUTF("Prompt evaluation failed. Try a shorter prompt.");
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) return env->NewStringUTF("Could not initialize sampler.");
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    const int limit = std::clamp(max_tokens, 1, 512);
    for (int i = 0; i < limit; ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(g_vocab, token)) break;
        char piece[1024];
        const int32_t length = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, false);
        if (length > 0) output.append(piece, static_cast<size_t>(length));
        llama_sampler_accept(sampler, token);
        if (llama_decode(g_context, llama_batch_get_one(const_cast<llama_token *>(&token), 1)) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_hamari_mehenat_jobhunter_ai_NativeLlama_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_model();
}
