package de.mimuc.senseeverything.api;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
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

    public void getJson(String url, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, listener, errorListener);
        addToRequestQueue(jsonObjectRequest);
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

    public void postFile(String url, String fileName, String contentType, byte[] fileData, Map<String, String> formData, Map<String, String> headers, Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        MyMultipartRequest request = new MyMultipartRequest(Request.Method.POST, url, new Response.Listener<NetworkResponse>() {
            @Override
            public void onResponse(NetworkResponse response) {
                String resultResponse = new String(response.data);
                try {
                    JSONObject result = new JSONObject(resultResponse);

                    listener.onResponse(result);
                } catch (JSONException e) {
                    errorListener.onErrorResponse(new VolleyError("could not parse response"));
                }
            }
        }, errorListener) {
            @Override
            protected Map<String, String> getParams() {
                return formData;
            }

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                params.put("file", new DataPart(fileName, fileData, contentType));
                return params;
            }
        };

        if (!headers.isEmpty()) {
            request.setHeaders(headers);
        }
        addToRequestQueue(request);
    }
}
