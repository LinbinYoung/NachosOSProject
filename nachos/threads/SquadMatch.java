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
	private Lock warLock;
	private Lock wizLock;
	private Lock thiLock;
	private int warCount;
	private int wizCount;
	private int thiCount;

	/**
	 * Allocate a new SquadMatch for matching players of different abilities into a
	 * squad to play a match.
	 */
	public SquadMatch() {
//		this.list = new LinkedList<>();
		this.warLock = new Lock();
		this.wizLock = new Lock();
		this.thiLock = new Lock();
		this.warCond = new Condition(warLock);
		this.wizCond = new Condition(wizLock);
		this.thiCond = new Condition(thiLock);
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
//		Runnable warrior = new Runnable() {
//			public void run() {
//				System.out.println("Warrior marching");
//			}
//		};
		boolean intStatus = Machine.interrupt().disable();
//		KThread kth = new KThread(warrior);
		
		if (this.wizCount >= 1 && this.thiCount >= 1) {
			System.out.println("Debugging war 001");
			this.wizCount--;
			this.thiCount--;
			KThread.yield();
			if(!wizLock.isHeldByCurrentThread()) wizLock.acquire();
			wizCond.wake();
			wizLock.release();
			thiLock.acquire();
			thiCond.wake();
			thiLock.release();
//			kth.ready();
		} else {
			this.warCount++;
			System.out.println("Warriors count"+this.warCount);
//			kth.ready();
//			KThread.yield();
			if(!warLock.isHeldByCurrentThread()) warLock.acquire();
			this.warCond.sleep();
			warLock.release();
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
//		Runnable wizard = new Runnable() {
//			public void run() {
//				System.out.println("Wizard marching");
//			}
//		};
		boolean intStatus = Machine.interrupt().disable();
//		KThread kth = new KThread(wizard);
		
		if (this.warCount >= 1 && this.thiCount >= 1) {
			System.out.println("Debugging wiz 001");
			this.warCount--;
			this.thiCount--;
			KThread.yield();
			if(!warLock.isHeldByCurrentThread()) warLock.acquire();
			warCond.wake();
			warLock.release();
			thiLock.acquire();
			thiCond.wake();
			thiLock.release();
//			kth.ready();
		} else {
			this.wizCount++;
//			kth.ready();
//			KThread.yield();
			if(!wizLock.isHeldByCurrentThread()) wizLock.acquire();
			this.wizCond.sleep();
			wizLock.release();
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
//		Runnable thief = new Runnable() {
//			public void run() {
//				System.out.println("Thief marching");
//			}
//		};
		boolean intStatus = Machine.interrupt().disable();
//		KThread kth = new KThread(thief);
		System.out.println("Debugging 002");
		if (this.warCount >= 1 && this.wizCount >= 1) {
			System.out.println("Debugging thi 001");
			this.warCount--;
			this.wizCount--;
			KThread.yield();
//			System.out.println("Debugging 003 in If");
			if(!warLock.isHeldByCurrentThread()) warLock.acquire();
			
			warCond.wake();
			warLock.release();
			wizLock.acquire();
			wizCond.wake();
			wizLock.release();
//			kth.ready();
			System.out.println("Debugging thi 002");
		} else {
			this.thiCount++;
			
//			kth.ready();
			
//			KThread.yield();
			if(!thiLock.isHeldByCurrentThread()) thiLock.acquire();
			this.thiCond.sleep();
			thiLock.release();
		}
		Machine.interrupt().restore(intStatus);
//		boolean intStatus = Machine.interrupt().disable();
//		if (this.thiCount >= Math.max(this.warCount, this.wizCount)) {
//			this.thiCount++;
//			Condition cond = new Condition(new Lock());
//			this.list.add(cond);
//			
//			KThread kth = new KThread();
//			kth.ready();
//			KThread.yield();
//
//			cond.sleep();
//		} else if (this.warCount >= 1 || this.wizCount >= 1) {
//			this.warCount--;
//			this.wizCount--;
//			KThread kth = new KThread();
//			kth.ready();
//			
//			KThread.yield();
//			this.list.remove().wakeAll();
//		} else {
//			this.thiCount++;
//			KThread kth = new KThread();
//			kth.ready();
//			
//			KThread.yield();
//			list.get(this.thiCount - 1).sleep();
//		}
//		Machine.interrupt().restore(intStatus);
	}

	// Place SquadMatch test code inside of the SquadMatch class.
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
		// Run the threads.
		w1.fork();
		z1.fork();
		t1.fork();
		
		// if you have join implemented, use the following:
		w1.join();
		z1.join();
		t1.join();
//		
		// if you do not have join implemented, use yield to allow
		// time to pass...10 yields should be enough
//		for (int i = 0; i < 10; i++) {
//			KThread.currentThread().yield();
//		}
	}

	public static void selfTest() {
		squadTest1();
	}
}
