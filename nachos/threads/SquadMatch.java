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
    	this.ConditionLock = new Lock();
    	this.cv_wa = new Condition(this.ConditionLock);
    	this.cv_wi = new Condition(this.ConditionLock);
    	this.cv_th = new Condition(this.ConditionLock);
    	this.warrier = 0;
    	this.wizard = 0;
    	this.thief = 0;
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
    	boolean intStatus = Machine.interrupt().disable();
    	this.ConditionLock.acquire();
    	if (this.wizard != 0 && this.thief != 0) {
    		// we need to wake up wizard and Thief then
    		this.wizard --;
    		this.thief --;
    		cv_wi.wake();
    		cv_th.wake();
    	}else {
    		this.warrier ++;
    		cv_wa.sleep();
    	}
    	this.ConditionLock.release();
    	Machine.interrupt().restore(intStatus);
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
    	boolean intStatus = Machine.interrupt().disable();
    	this.ConditionLock.acquire();
    	if (this.warrier != 0 && this.thief != 0) {
    		// we need to wake up wizard and Thief then
    		this.thief --;
    		this.warrier --;
    		cv_wa.wake();
    		cv_th.wake();
    	}else {
    		this.wizard ++;
    		cv_wi.sleep();
    	}
    	this.ConditionLock.release();
    	Machine.interrupt().restore(intStatus);
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
    	boolean intStatus = Machine.interrupt().disable();
    	this.ConditionLock.acquire();
    	if (this.warrier != 0 && this.wizard != 0) {
    		// we need to wake up wizard and Thief then
    		this.wizard --;
    		this.warrier --;
    		cv_wa.wake();
    		cv_wi.wake();
    	}else {
    		this.thief ++;
    		cv_th.sleep();
    	}
    	this.ConditionLock.release();
    	Machine.interrupt().restore(intStatus);
    }
    
    private static Condition cv_wa;
    private static Condition cv_wi;
    private static Condition cv_th;
    private static Lock ConditionLock;
    private static int warrier;
    private static int wizard;
    private static int thief;
    
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
    	
    	KThread w3 = new KThread( new Runnable () {
    		public void run() {
    		    match.Warrier();
    		    System.out.println ("w3 matched");
    		}
    	    });
    	w1.setName("w3");

    	// Run the threads.
    	w1.fork();
    	z1.fork();
    	t1.fork();
    	t2.fork();
    	z2.fork();
    	w2.fork();
    	w3.fork();

    	// if you have join implemented, use the following:
//    	w1.join();
//    	z1.join();
//    	t1.join();
//    	w2.join();
//    	t2.join();
//    	w3.join();
//    	z2.join();
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
