// This is DownNodeRecoveryTester.java
// Author:  Brian Williams ( brian.williams@couchbase.com )
// Date:    March 27, 2015
// 
// This is a Java command-line application that performs a test when a node is down
// It connects to 3 nodes, and it has 3 specific keys that are known to hash to each of the 3 nodes.
// To use this program, run it against a 3-node cluster in a steady state, and then take out
// a node such as node 2.
//
// You should observe timeouts for node 2's key, until such time as you Fail Over the node,
// at which point, either node 1 or node 3 will take over, and you can observe this in the UI
//
// At the point at which you Fail Over node 2, both get() and getFromReplica() will briefly fail.
// After that, get() will start succeeding
//
// Dependencies:
//   693936 Jan  5 07:26 rxjava-1.0.4.jar
//   358544 Mar 26 14:41 java-client-2.1.2-dp.jar
//  3905643 Mar 26 14:42 core-io-1.1.2-dp.jar
//
// Developed using Eclipse Version: Luna Service Release 1 (4.4.1)
// and JavaSE-1.8


package com.couchbase.support;

import com.couchbase.client.java.*;
import com.couchbase.client.java.document.*;
import com.couchbase.client.java.env.*;

import java.util.concurrent.TimeoutException;
import java.util.logging.*;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;


class TestResult {

	// test information
	String keyToTest;
	int nodeNumber;
	
	// results
	int returnedDocSizeGet;
	int returnedDocSizeReplica;
	boolean exceptionFromGet;
	boolean exceptionFromReplica;
	long getTimeTaken;
	long getFromReplicaTimeTaken;

	public TestResult() {	
		// constructor
		keyToTest               = "Please specify a key name";
		nodeNumber              = -1;
		returnedDocSizeGet      = -1;
		returnedDocSizeReplica  = -1;
		exceptionFromGet        = false;
		exceptionFromReplica    = false;
		getTimeTaken            = 0;
		getFromReplicaTimeTaken = 0;
	}
}



public class DownNodeRecoveryTester {

	// Replace with your cluster nodes	
	static String node1Name  = "10.0.0.1";
	static String node2Name  = "10.0.0.2";
	static String node3Name  = "10.0.0.3";
	static String bucketName = "beer-sample";
	
	// This test relies on using certain keys that are known to hash to certain nodes
	// using something like:
	//
	// /opt/couchbase/bin/curl -s -u Administrator:password http://localhost:8091/pools/default/buckets/beer-sample | ./tools/vbuckettool  - 21st_amendment_brewery_cafe-21a_ipa
	//
	static String keyThatHashesToNode1 = "shmaltz_enterprises";
	static String keyThatHashesToNode2 = "21st_amendment_brewery_cafe-21a_ipa";
	static String keyThatHashesToNode3 = "21st_amendment_brewery_cafe-oyster_point_oyster_stout";
	// 21st_amendment_brewery_cafe also goes to node 2
	static long globalTimeout          = 2000;
	static int sleepInterval           = 500;        	 // 500 milliseconds between tests
	static boolean debuggingMax        = false;
		
	private static Cluster createCouchbaseCluster() {
		CouchbaseEnvironment env = DefaultCouchbaseEnvironment.builder().build();
		// More than one node is specified, for maximum robustness
		Cluster cluster = CouchbaseCluster.create(env, node1Name, node2Name, node3Name);
		return cluster;
	}

	static TestResult testOne(int nodeNumber, Bucket bucket, String keyToTest) {
		
		System.out.println("########## About to testOne on node " + nodeNumber + " ##########");
		
		long t1 = 0, t2 = 0, timeTaken = 0;
		JsonDocument jsonDoc = null;
		List<JsonDocument> jsonDocList = null;
		int jsonDocStringLength = -1;
		int jsonDocListSize = -1;
		
		TestResult tr = new TestResult();
		tr.keyToTest = keyToTest;
		tr.nodeNumber = nodeNumber;
		
		// TRY A REGULAR GET
		
		try {	
			t1 = System.currentTimeMillis();
			//jsonDoc  = bucket.get(keyToTest,globalTimeout, TimeUnit.MILLISECONDS);
			jsonDoc  = bucket.get(keyToTest);
			jsonDocStringLength = jsonDoc.toString().length();
			tr.returnedDocSizeGet = jsonDocStringLength;
		}
		catch (RuntimeException e) {
			if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
				// No need to print stack trace, I know what it is.
				System.out.println("--------------  Doing get() Caught runtime/timeout exception  --------------");
				tr.exceptionFromGet = true;
			}
			else {
				System.out.println("--------------  Doing get() Caught unexpected runtime exception  --------------");
				e.printStackTrace();			
				System.exit(1);
			}
		}
		catch (Exception e) {
			System.out.println("--------------  Doing get() Caught other unexpected exception  --------------");			
			e.printStackTrace();			
			System.exit(1);
		}
		
		t2 = System.currentTimeMillis();
		timeTaken = t2 - t1;
		tr.getTimeTaken = timeTaken;
		System.out.println("Node: " + nodeNumber + " get() operation took " + timeTaken + " ms. Doc length: " + jsonDocStringLength);	
		
		// TRY TO GET FROM REPLICA
		
		// reset reused variable
		jsonDocStringLength = -1;
		
		try {
			t1 = System.currentTimeMillis();
			jsonDocList  = bucket.getFromReplica(keyToTest, ReplicaMode.FIRST);
			jsonDoc = jsonDocList.get(0);
			jsonDocListSize = jsonDocList.size();
			jsonDocStringLength = jsonDoc.toString().length();
			tr.returnedDocSizeReplica = jsonDocStringLength;						
		} 
		catch (RuntimeException e2) {
			if (e2.getCause() instanceof java.util.concurrent.TimeoutException) {
				// No need to print stack trace, I know what it is.
				System.out.println("--------------  Doing getFromReplica() Caught runtime/timeout exception  --------------");
				tr.exceptionFromReplica = true;
			}
			else {
				System.out.println("--------------  Doing getFromReplica() Caught unexpected runtime exception  --------------");
				e2.printStackTrace();			
				System.exit(1);
			}
		}
		catch (Exception e3 ) {
			System.out.println("--------------  Doing getFromReplica() Caught other unexpected exception  --------------");
			e3.printStackTrace();
			System.exit(1);
		}
		t2 = System.currentTimeMillis();
		timeTaken = t2 - t1;
		tr.getFromReplicaTimeTaken = timeTaken;
		System.out.println("Node: " + nodeNumber + " getFromReplica() operation took " + timeTaken + " ms. List length: " + jsonDocListSize + " Doc length: " + jsonDocStringLength);	

		System.out.println("########## Finished testOne on node " + nodeNumber + " ##########");
		
		return tr;
		
	} // testOne key
	
	
// Sample report from the method below:
//
//	Node #     get() exception  getFromReplica() exception  get() time  replica time  get size  replica size
//	---------  ---------------  --------------------------  ----------  ------------  --------  ------------
//	        1            false                        true          26          2501       406            -1
//	        2             true                       false        2501            13        -1           613
//	        3            false                        true           2          2500       656            -1	
	
	static void printTestResultList(List<TestResult> listOfTestResults) {
	
		System.out.println("Node #     get() exception  getFromReplica() exception  get() time  replica time  get size  replica size");
		System.out.println("---------  ---------------  --------------------------  ----------  ------------  --------  ------------");
		
		for( TestResult tr: listOfTestResults) {
			System.out.format("%9d  %15b  %26b  %10d  %12d  %8d  %12d\n", 
					tr.nodeNumber, 
					tr.exceptionFromGet, 
					tr.exceptionFromReplica,
					tr.getTimeTaken, 
					tr.getFromReplicaTimeTaken, 
					tr.returnedDocSizeGet, 
					tr.returnedDocSizeReplica);
		}
	}

	
	public static void main(String[] args) {

		System.out.println("Starting...");
		
		if ( debuggingMax ) {
			Logger.getLogger("com.couchbase.client").setLevel(Level.FINEST);
			for(Handler h : Logger.getLogger("com.couchbase.client").getParent().getHandlers()) {
				if(h instanceof ConsoleHandler) {
					h.setLevel(Level.FINEST);
				}
			}
			Properties systemProperties = System.getProperties();
			systemProperties.put("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.Log4JLogger");
			System.setProperties(systemProperties);
			// org.apache.log4j.BasicConfigurator.configure();
		} // debugging

		System.out.println("--------------  About to open Couchbase cluster  --------------");
		Cluster cluster = createCouchbaseCluster(); 

		System.out.println("--------------  About to open the bucket  --------------");
		Bucket bucket = cluster.openBucket(bucketName);	
		
		long timeNow;
		
		List<TestResult> testResultList = new ArrayList<TestResult>();
		
		System.out.println("--------------  About to enter the main loop  --------------");

		try {

		while (true) {

		timeNow = System.currentTimeMillis();

		System.out.println("--------------  About to perform tests on keys " + timeNow + "  --------------");

		TestResult resultFromNode1 = testOne(1, bucket, keyThatHashesToNode1);
		TestResult resultFromNode2 = testOne(2, bucket, keyThatHashesToNode2);
		TestResult resultFromNode3 = testOne(3, bucket, keyThatHashesToNode3);
		
		testResultList.add(resultFromNode1);
		testResultList.add(resultFromNode2);
		testResultList.add(resultFromNode3);
		
		printTestResultList(testResultList);
		
		testResultList.clear();
		
		System.out.println("---------------- About to sleep... ------------");

		// Sleepy Time
		try {
			Thread.sleep(sleepInterval);
		}
		catch (Exception e) {
			System.out.println("--------------  Caught exception while sleep()ing  --------------");
			e.printStackTrace();
		}

		} // main loop

		} catch ( Throwable t ) {

			System.out.println("--------------  Caught THROWABLE in main infinite loop  --------------");

			t.printStackTrace();

			System.out.println("Goodbye...");
		}

	}

}