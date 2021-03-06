/**
 * Copyright (c) 2014, Yahoo!, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.yahoo.ycsb.db;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.StringByteIterator;

import org.tarantool.TarantoolConnection16;
import org.tarantool.TarantoolConnection16Impl;
import org.tarantool.TarantoolException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TarantoolClient extends DB {

	public static final String HOST_PROPERTY  = "tarantool.host";
	public static final String PORT_PROPERTY  = "tarantool.port";
	public static final String SPACE_PROPERTY = "tarantool.space";

	public static final String DEFAULT_HOST   = "localhost";
	public static final int    DEFAULT_PORT   = 3301;
	public static final int    DEFAULT_SPACE  = 1024;

	private static final Logger logger = Logger.getLogger(TarantoolClient.class.getName());
	private TarantoolConnection16 connection;
	private int spaceNo;

	public void init() throws DBException {
		Properties props = getProperties();

		int port = DEFAULT_PORT;
		String portString = props.getProperty(PORT_PROPERTY);
		if (portString != null) {
			port = Integer.parseInt(portString);
		}

		String host = props.getProperty(HOST_PROPERTY);
		if (host == null) {
			host = DEFAULT_HOST;
		}

		spaceNo = DEFAULT_SPACE;
		String spaceString = props.getProperty(SPACE_PROPERTY);
		if (spaceString != null) {
			spaceNo = Integer.parseInt(spaceString);
		}

		try {
			this.connection = new TarantoolConnection16Impl(host, port);
		} catch (Exception exc) {
			logger.log(Level.SEVERE,"Can't initialize Tarantool connection", exc);
			return;
		}
	}

	public void cleanup() throws DBException{
		this.connection.close();
	}

	@Override
	public Status insert(String table, String key, HashMap<String, ByteIterator> values) {
		int j = 0;
		String[] tuple = new String[1 + 2 * values.size()];
		tuple[0] = key;
		for (Map.Entry<String, ByteIterator> i: values.entrySet()) {
			tuple[j + 1] = i.getKey();
			tuple[j + 2] = i.getValue().toString();
			j += 2;
		}
		try {
			this.connection.replace(this.spaceNo, tuple);
		} catch (TarantoolException exc) {
			logger.log(Level.SEVERE,"Can't insert element", exc);
			return Status.ERROR;
		}
		return Status.OK;
	}

	private HashMap<String, ByteIterator> tuple_convert_filter (List<String> input,
			Set<String> fields) {
		HashMap<String, ByteIterator> result = new HashMap<String, ByteIterator>();
		if (input == null)
			return result;
		for (int i = 1; i < input.toArray().length; i += 2)
			if (fields == null || fields.contains(input.get(i)))
				result.put(input.get(i), new StringByteIterator(input.get(i+1)));
		return result;
	}

	@Override
	public Status read(String table, String key, Set<String> fields,
			HashMap<String, ByteIterator> result) {
		try {
			List<String> response;
			response = this.connection.select(this.spaceNo, 0, Arrays.asList(key), 0, 1, 0);
			result = tuple_convert_filter(response, fields);
			return Status.OK;
		} catch (TarantoolException exc) {
			logger.log(Level.SEVERE,"Can't select element", exc);
			return Status.ERROR;
		} catch (NullPointerException exc) {
			return Status.ERROR;
		}
	}

	@Override
	public Status scan(String table, String startkey,
			int recordcount, Set<String> fields,
			Vector<HashMap<String, ByteIterator>> result) {
		List<List<String>> response;
		try {
			response = this.connection.select(this.spaceNo, 0, Arrays.asList(startkey), 0, recordcount, 6);
		} catch (TarantoolException exc) {
			logger.log(Level.SEVERE,"Can't select range elements", exc);
			return Status.ERROR;
		} catch (NullPointerException exc) {
			return Status.ERROR;
		}
		for(List<String> i: response) {
			HashMap<String, ByteIterator> temp = tuple_convert_filter(i, fields);
			if (!temp.isEmpty())
				result.add((HashMap<String, ByteIterator>) temp.clone());
		}
		return Status.OK;
	}

	@Override
	public Status delete(String table, String key) {
		try {
			this.connection.delete(this.spaceNo, Arrays.asList(key));
		} catch (TarantoolException exc) {
			logger.log(Level.SEVERE,"Can't delete element", exc);
			return Status.ERROR;
		} catch (NullPointerException e) {
			return Status.ERROR;
		}
		return Status.OK;
	}
	@Override
	public Status update(String table, String key,
			HashMap<String, ByteIterator> values) {
		int j = 0;
		String[] tuple = new String[1 + 2 * values.size()];
		tuple[0] = key;
		for (Map.Entry<String, ByteIterator> i: values.entrySet()) {
			tuple[j + 1] = i.getKey();
			tuple[j + 2] = i.getValue().toString();
			j += 2;
		}
		try {
			this.connection.replace(this.spaceNo, tuple);
		} catch (TarantoolException exc) {
			logger.log(Level.SEVERE,"Can't replace element", exc);
			return Status.ERROR;
		}
		return Status.OK;

	}
}
