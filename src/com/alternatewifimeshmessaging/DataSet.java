package com.alternatewifimeshmessaging;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

enum CompressionType {
	NONE, GZIP
};

public class DataSet {

	private ArrayList<Data> dataList;
	private CompressionType compression;

	public DataSet(CompressionType compression) {
		this.compression = compression;
	}

	public DataSet() {
		this.compression = CompressionType.NONE;
	}

	// Creates a JSON array from all the data, and compresses it

	public String encode() {

		JSONArray array = new JSONArray();

		for (Data data : dataList) {
			array.put(data.encode(compression));
		}

		return array.toString();

	}

	public ArrayList<String> toArrayList() {
		
		ArrayList<String> stringList = new ArrayList<String>();
		
		for (Data data : dataList) {
			stringList.add(data.message);
		}
		
		return stringList;

	}

	public void insert(Data data) {
		dataList.add(data);
	}

	public void insert(String JSON) {
		insert(new Data(JSON));
	}

	public void insert(JSONObject object) {
		insert(new Data(object));
	}

	// Decodes a JSON array and inserts data
	
	public void decode(String JSON) {

		JSONArray array = null;

		try {
			array = new JSONArray(JSON);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}

		for (int n = 0; n < array.length(); n++) {

			try {
				insert(array.getJSONObject(n));
			} catch (JSONException e) {
				e.printStackTrace();
			}

		}
	}

}
