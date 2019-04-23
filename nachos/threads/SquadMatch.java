package nachos.threads;

import java.util.*;

import nachos.machine.*;

/**
 * A <i>SquadMatch</i> groups together player threads of the three different
 * abilities to play matches with each other. Implement the class
 * <i>SquadMatch</i> using <i>Lock</i> and <i>Condition</i> to synchronize
 * player threads into such groups.
 */
public class SquadMatch {

//	private LinkedList<Condition> list;
	private Condition warCond;
	private Condition wizCond;
	private Condition thiCond;
	private Lock lock;
	private int warCount;
	private int wizCount;
	private int thiCount;

	/**
	 * Allocate a new SquadMatch for matching players of different abilities into a
	 * squad to play a match.
	 */
	public SquadMatch() {
//		this.list = new LinkedList<>();
		this.lock = new Lock();
		this.warCond = new Condition(lock);
		this.wizCond = new Condition(lock);
		this.thiCond = new Condition(lock);
		this.warCount = 0;
		this.wizCount = 0;
		this.thiCount = 0;
	}

	/**
	 * Wait to form a squad with wizard and thief threads, only returning once all
	 * three kinds of player threads have called into this SquadMatch. A squad
	 * always has three threads, and can only be formed by three different kinds of
	 * threads. Many matches may be formed over time, but any one player thread can
	 * be assigned to only one match.
	 */
	public void warrior() {
		boolean intStatus = Machine.interrupt().disable();
		if (this.wizCount >= 1 && this.thiCount >= 1) {
			this.wizCount--;
			this.thiCount--;
			if (!lock.isHeldByCurrentThread())
				lock.acquire();
			wizCond.wake();
			thiCond.wake();
//			KThread.yield();
			lock.release();
		} else {
			this.warCount++;
			if (!lock.isHeldByCurrentThread())
				lock.acquire();
			this.warCond.sleep();
			lock.release();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wait to form a squad with warrior and thief threads, only returning once all
	 * three kinds of player threads have called into this SquadMatch. A squad
	 * always has three threads, and can only be formed by three different kinds of
	 * threads. Many matches may be formed over time, but any one player thread can
	 * be assigned to only one match.
	 */
	public void wizard() {
		boolean intStatus = Machine.interrupt().disable();
//		KThread kth = new KThread(wizard);

		if (this.warCount >= 1 && this.thiCount >= 1) {
//			System.out.println("Debugging wiz 001");
			this.warCount--;
			this.thiCount--;
			if (!lock.isHeldByCurrentThread())
				lock.acquire();
			warCond.wake();
			thiCond.wake();
//			KThread.yield();
			lock.release();
		} else {
			this.wizCount++;
			if (!lock.isHeldByCurrentThread())
				lock.acquire();
			this.wizCond.sleep();
			lock.release();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wait to form a squad with warrior and wizard threads, only returning once all
	 * three kinds of player threads have called into this SquadMatch. A squad
	 * always has three threads, and can only be formed by three different kinds of
	 * threads. Many matches may be formed over time, but any one player thread can
	 * be assigned to only one match.
	 */
	public void thief() {
		boolean intStatus = Machine.interrupt().disable();
		if (this.warCount >= 1 && this.wizCount >= 1) {
			this.warCount--;
			this.wizCount--;
			if (!lock.isHeldByCurrentThread())
				lock.acquire();
			warCond.wake();
			wizCond.wake();
//			KThread.yield();
			lock.release();
		} else {
			this.thiCount++;
			if (!lock.isHeldByCurrentThread())
				lock.acquire();
			this.thiCond.sleep();
			lock.release();
		}
		Machine.interrupt().restore(intStatus);
	}

	public static void squadTest1() {
		final SquadMatch match = new SquadMatch();

		// Instantiate the threads
		KThread w1 = new KThread(new Runnable() {
			public void run() {
				match.warrior();
				System.out.println("w1 matched");
			}
		});
		w1.setName("w1");

		KThread z1 = new KThread(new Runnable() {
			public void run() {
				match.wizard();
				System.out.println("z1 matched");
			}
		});
		z1.setName("z1");

		KThread t1 = new KThread(new Runnable() {
			public void run() {
				match.thief();
				System.out.println("t1 matched");
			}
		});
		t1.setName("t1");

		KThread t2 = new KThread(new Runnable() {
			public void run() {
				match.thief();
				System.out.println("t2 matched");
			}
		});
		t2.setName("t2");

		KThread z2 = new KThread(new Runnable() {
			public void run() {
				match.wizard();
				System.out.println("z2 matched");
			}
		});
		z2.setName("z2");

		KThread w2 = new KThread(new Runnable() {
			public void run() {
				match.warrior();
				System.out.println("w2 matched");
			}
		});
		w2.setName("w2");
		
		KThread z3 = new KThread(new Runnable() {
			public void run() {
				match.wizard();
				System.out.println("z3 matched");
			}
		});
		z3.setName("z3");

		KThread w3 = new KThread(new Runnable() {
			public void run() {
				match.warrior();
				System.out.println("w3 matched");
			}
		});
		w3.setName("w3");
		
		KThread t3 = new KThread(new Runnable() {
			public void run() {
				match.thief();
				System.out.println("t3 matched");
			}
		});
		t3.setName("t3");

		// Run the threads.
		w1.fork();
		z1.fork();
		t1.fork();
		t2.fork();
		z2.fork();
		w2.fork();
		z3.fork();
//		w3.fork();
		t3.fork();

		// if you have join implemented, use the following:
		w1.join();
//		System.out.println("Debugging");
//		z1.join();
//		t1.join();
//		t2.join();
//		w2.join();
//		z2.join();
//		z3.join();
//		t3.join();
		// if you do not have join implemented, use yield to allow
		// time to pass...10 yields should be enough
//	     for (int i = 0; i < 10; i++) {
//	         KThread.currentThread().yield();
//	     }
	}

	public static void selfTest() {
		squadTest1();
	}
}
