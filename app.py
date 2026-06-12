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
        if input_hash == CORRECT_
      
