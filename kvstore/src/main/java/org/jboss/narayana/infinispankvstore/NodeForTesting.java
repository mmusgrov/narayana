package org.jboss.narayana.infinispankvstore;

import java.io.IOException;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

/**
 * Runs an infinispan node for testing purposes that has knowledge of a
 * replicated cache store and a distributed cach store
 * 
 * @author patches
 * 
 */
public class NodeForTesting {

	public static void main(String[] args) {
		try {
			EmbeddedCacheManager manager = new DefaultCacheManager("multi-cache-cfg.xml");
			manager.getCache("distributed-cache");
			manager.getCache("replication-cache");
			
			System.out.println("Node Started Successfully");
		} catch (IOException ioe) {
			System.out.println("Node Failed to Start - no Config File");
		} catch (Exception e) {
			System.out.print("Node Failed:\n" + e.getMessage());
		}

	}

}
