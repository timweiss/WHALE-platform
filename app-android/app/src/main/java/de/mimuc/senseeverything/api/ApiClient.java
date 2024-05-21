package de.mimuc.senseeverything.api;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

public class ApiClient {
    private static ApiClient instance;
    private RequestQueue requestQueue;

    private ApiClient(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public <T> void addToRequestQueue(Request<T> request) {
        requestQueue.add(request);
    }

    // GET request
    public void get(String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url, listener, errorListener);
        addToRequestQueue(stringRequest);
    }

    // POST request
    public void post(String url, JSONObject jsonRequest, Map<String, String> headers, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, jsonRequest, listener, errorListener);
        if (!headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                try {
                    jsonObjectRequest.getHeaders().put(entry.getKey(), entry.getValue());
                } catch (AuthFailureError e) {
                    throw new RuntimeException(e);
                }
            }
        }
        addToRequestQueue(jsonObjectRequest);
    }

    public void postArray(String url, JSONArray jsonRequest, Map<String, String> headers, Response.Listener<JSONArray> listener, Response.ErrorListener errorListener) {
        MyJsonArrayRequest request = new MyJsonArrayRequest(Request.Method.POST, url, jsonRequest, listener, errorListener);
        if (!headers.isEmpty()) {
            request.setHeaders(headers);
        }
        addToRequestQueue(request);
    }
}
