import streamlit as st
import autogen
import hashlib
import json

st.set_page_config(page_title="Hamari Mehnat AI Portal", layout="wide")

# "1234" ka secure SHA-256 hash
CORRECT_PASSWORD_HASH = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"

if "logged_in" not in st.session_state:
    st.session_state.logged_in = False

if not st.session_state.logged_in:
    st.title("🔐 Secure Login - Hamari Mehnat AI")
    password = st.text_input("Enter Portal Password:", type="password")
    if st.button("Sign In"):
        input_hash = hashlib.sha256(password.encode()).hexdigest()
        if input_hash == CORRECT_PASSWORD_HASH:
            st.session_state.logged_in = True
            st.rerun()
        else:
            st.error("❌ Invalid Password!")
else:
    st.title("🤖 Hamari Mehnat AI - Multi-Agent Portal")
    user_input = st.text_area("Describe the software or feature you want to build:")
    
    if st.button("🚀 Run Agent Discussion"):
        if user_input:
            with st.spinner("🚀 Team is brainstorming... Please wait."):
                
                try:
                    GROQ_KEY = st.secrets["GROQ_API_KEY"]
                except KeyError:
                    st.error("🔑 API Key missing! Please add 'GROQ_API_KEY' in Streamlit Advanced Settings -> Secrets.")
                    st.stop()
                
                config_list = [{
                    "model": "llama-3.3-70b-versatile", 
                    "api_key": GROQ_KEY, 
                    "api_type": "openai", 
                    "base_url": "https://api.groq.com/openai/v1"
                }]
                
                user_proxy = autogen.UserProxyAgent(name="User_Proxy", human_input_mode="NEVER", code_execution_config={"work_dir": "workspace", "use_docker": False})
                architect = autogen.AssistantAgent(name="Architect", llm_config={"config_list": config_list}, system_message="You are a Senior Software Architect. Design architecture only.")
                coder = autogen.AssistantAgent(name="Coder", llm_config={"config_list": config_list}, system_message="You are a Senior Developer. Write clean Python code.")
                tester = autogen.AssistantAgent(name="Tester", llm_config={"config_list": config_list}, system_message="You are a QA Engineer. Review code and find bugs.")

                groupchat = autogen.GroupChat(agents=[user_proxy, architect, coder, tester], messages=[], max_round=5)
                manager = autogen.GroupChatManager(groupchat=groupchat, llm_config={"config_list": config_list})
                
                user_proxy.initiate_chat(manager, message=user_input)
                
                st.success("🎉 Process Completed!")
                
                chat_data = []
                for msg in groupchat.messages:
                    name = msg.get('name', 'Agent')
                    content = msg.get('content', '')
                    chat_data.append({"Agent": name, "Message": content})
                    with st.chat_message(name):
                        st.markdown(f"**{name}:** {content}")
                
                json_string = json.dumps(chat_data, indent=4)
                st.download_button(label="📥 Download Discussion History", file_name="history.json", mime="application/json", data=json_string)
                
