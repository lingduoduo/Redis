import json
import os

import streamlit as st
import redis
import pandas as pd


@st.cache_resource
def get_client():
    return redis.Redis(
        host=os.getenv("REDIS_HOST", "localhost"),
        port=int(os.getenv("REDIS_PORT", "6380")),
        db=int(os.getenv("REDIS_DB", "0")),
        decode_responses=True,
    )


r = get_client()


def get_recommendations(query, top_k=5):
    query = query.lower()
    keys = list(r.scan_iter(match="item:*", count=500))
    if not keys:
        return []

    pipe = r.pipeline(transaction=False)
    for key in keys:
        pipe.get(key)
    values = pipe.execute()

    all_items = []
    for value in values:
        try:
            all_items.append(json.loads(value))
        except (TypeError, json.JSONDecodeError):
            continue

    return [
        item
        for item in all_items
        if query in item.get("title", "").lower()
        or query in item.get("description", "").lower()
    ][:top_k]


st.title("Content-Based Recommendation Demo")

query = st.text_input("Enter your search query:", placeholder="e.g. payroll setup")
if query:
    st.write(f"Showing recommendations for: **{query}**")

    results = get_recommendations(query)

    if results:
        df = pd.DataFrame(results)
        st.dataframe(df)
    else:
        st.info("No matching results found.")
