import streamlit as st
import redis
import json
import pandas as pd

# --- Set up Redis connection ---
r = redis.Redis(host='localhost', port=6380, decode_responses=True)


# --- Dummy function to simulate recommendation ---
def get_recommendations(query, top_k=5):
    # Option 1: Retrieve all keys and do simple filter (mock)
    all_keys = r.keys("item:*")
    all_items = [json.loads(r.get(k)) for k in all_keys]

    # Simple filter: title or description contains the query (case-insensitive)
    filtered = [item for item in all_items if
                query.lower() in item["title"].lower() or query.lower() in item["description"].lower()]

    return filtered[:top_k]  # top K results


# --- Streamlit UI ---
st.title("🔍 Content-Based Recommendation Demo")

query = st.text_input("Enter your search query:", placeholder="e.g. payroll setup")
if query:
    st.write(f"Showing recommendations for: **{query}**")

    results = get_recommendations(query)

    if results:
        df = pd.DataFrame(results)
        st.dataframe(df)
    else:
        st.info("No matching results found.")
