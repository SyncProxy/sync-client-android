package com.syncproxy.syncclient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class JsonUtils {
    public static JSONObject deepMerge(JSONObject source, JSONObject target) throws JSONException {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.get(key);
            if (!target.has(key)) {
                // new value for "key":
                target.put(key, value);
            } else {
                if (value instanceof JSONArray) {
                    // Merge source and destination arrays with same name
                    JSONArray srcArray = (JSONArray) source.get(key);
                    JSONArray dstArray = (JSONArray) target.get(key);
                    for (int i = 0; i < srcArray.length(); i++)
                        dstArray.put(srcArray.get(i));
                } else if (value instanceof JSONObject) {
                    // existing value for "key" - recursively deep merge:
                    JSONObject valueJson = (JSONObject) value;
                    deepMerge(valueJson, target.getJSONObject(key));
                }
            }
        }
        return target;
    }
}
