/*
 * socket.io-java-client IOAcknowledge.java
 *
 * Copyright (c) 2012, Enno Boland
 * PROJECT DESCRIPTION
 * 
 * See LICENSE file for more information
 */
package socket;

/**
 * The Interface IOAcknowledge.
 */
public interface IOAcknowledge {
	
	/**
	 * Acknowledges a socket.io message.
	 *
	 * @param args may be all types which can be serialized by {@link org.json.JSONArray#put(Object)}
	 */
	void ack(Object... args);
}
