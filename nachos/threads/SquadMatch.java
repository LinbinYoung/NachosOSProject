package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>SquadMatch</i> groups together player threads of the 
 * three different abilities to play matches with each other.
 * Implement the class <i>SquadMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into such groups.
 */

public class SquadMatch {
    
    /**
     * Allocate a new SquadMatch for matching players of different
     * abilities into a squad to play a match.
     */
	
    public SquadMatch () {
    	this.map = new HashMap<>();
    	this.ConditionLock = new Lock();
    	this.cv_1 = new Condition(this.ConditionLock);
    	this.cv_2 = new Condition(this.ConditionLock);
    	this.cv_3 = new Condition(this.ConditionLock);
    	this.map.put("Warrier", this.cv_1);
    	this.map.put("Wizard", this.cv_2);
    	this.map.put("Thief", this.cv_3);
    	
    }

    /**
     * Wait to form a squad with Wizard and Thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    
    public void Warrier () {
    	/*
    	 * To Check whether Wizard and Thief in wait queue
    	 * if not, sleep the current method
    	 */
    	this.ConditionLock.acquire();
    	if (this.map.get("Wizard").getSize() != 0 && this.map.get("Thief").getSize() != 0) {
    		// we need to wake up wizard and Thief then
    		this.map.get("Wizard").wake();
    		this.map.get("Thief").wake();
    		this.map.get("Warrier").wake();
    	}else {
    		Condition temp = this.map.get("Warrier");
    		temp.sleep();
    	}
    	this.ConditionLock.release();
    }

    /**
     * Wait to form a squad with Warrior and Thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    
    public void Wizard () {
    	/*
    	 * To Check whether Warrier and Thief in wait queue
    	 * if not, sleep the current method
    	 */
    	this.ConditionLock.acquire();
    	if (this.map.get("Warrier").getSize() != 0 && this.map.get("Thief").getSize() != 0) {
    		// we need to wake up wizard and Thief then
    		this.map.get("Wizard").wake();
    		this.map.get("Thief").wake();
    		this.map.get("Warrier").wake();
    	}else {
    		Condition temp = this.map.get("Wizard");
    		temp.sleep();
    	}
    	this.ConditionLock.release();

    }

    /**
     * Wait to form a squad with Warrior and Wizard threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    
    public void Thief () {
    	/*
    	 * To Check whether Warrier and Wizard in wait queue
    	 * if not, sleep the current method
    	 */
    	this.ConditionLock.acquire();
    	if (this.map.get("Warrier").getSize() != 0 && this.map.get("Wizard").getSize() != 0) {
    		// we need to wake up wizard and Thief then
    		this.map.get("Wizard").wake();
    		this.map.get("Thief").wake();
    		this.map.get("Warrier").wake();
    	}else {
    		Condition temp = this.map.get("Thief");
    		temp.sleep();
    	}
    	this.ConditionLock.release();
    	
    }
    
    private static HashMap<String, Condition> map;
    private static Condition cv_1;
    private static Condition cv_2;
    private static Condition cv_3;
    private static Lock ConditionLock;
    
    public static void squadTest1 () {
    	final SquadMatch match = new SquadMatch();

    	// Instantiate the threads
    	KThread w1 = new KThread( new Runnable () {
    		public void run() {
    		    match.Warrier();
    		    System.out.println ("w1 matched");
    		}
    	    });
    	w1.setName("w1");

    	KThread z1 = new KThread( new Runnable () {
    		public void run() {
    		    match.Wizard();
    		    System.out.println ("z1 matched");
    		}
    	    });
    	z1.setName("z1");

    	KThread t1 = new KThread( new Runnable () {
    		public void run() {
    		    match.Thief();
    		    System.out.println ("t1 matched");
    		}
    	    });
    	t1.setName("t1");
    	
    	KThread t2 = new KThread( new Runnable () {
    		public void run() {
    		    match.Thief();
    		    System.out.println ("t2 matched");
    		}
    	    });
    	t1.setName("t2");
    	
    	KThread z2 = new KThread( new Runnable () {
    		public void run() {
    		    match.Wizard();
    		    System.out.println ("z2 matched");
    		}
    	    });
    	z1.setName("z2");
    	
    	KThread w2 = new KThread( new Runnable () {
    		public void run() {
    		    match.Warrier();
    		    System.out.println ("w2 matched");
    		}
    	    });
    	w1.setName("w2");

    	// Run the threads.
    	w1.fork();
    	z1.fork();
    	t1.fork();
    	t2.fork();
    	z2.fork();
    	w2.fork();

    	// if you have join implemented, use the following:
    	w1.join();
    	z1.join();
    	t1.join();
    	// if you do not have join implemented, use yield to allow
    	// time to pass...10 yields should be enough
//    	for (int i = 0; i < 10; i++) {
//    	    KThread.currentThread().yield();
//    	}
        }
        
        public static void selfTest() {
    	    squadTest1();
        }
        
}
