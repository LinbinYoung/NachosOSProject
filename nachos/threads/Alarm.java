package nachos.threads;

import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		// Initialized the t_queue in the constructor
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current thread
	 * to yield, forcing a context switch if there is another thread that should be
	 * run.
	 */
	public void timerInterrupt() {
		long cur_systime = Machine.timer().getTime();
		boolean intStatus = Machine.interrupt().disable();
		while (!t_queue.isEmpty() && t_queue.peek().waittime <= cur_systime) {
			t_queue.poll().thread.ready();
		}
		Machine.interrupt().restore(intStatus);
		KThread.yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up in
	 * the timer interrupt handler. The thread must be woken up (placed in the
	 * scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */

	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
//         long wakeTime = Machine.timer().getTime() + x;
//         while (wakeTime > Machine.timer().getTime())
//         KThread.yield();
		if(x<=0) return;
		long waketime = Machine.timer().getTime() + x;
		t_queue.add(new Thread_with_time(KThread.currentThread(), waketime));
		boolean intStatus = Machine.interrupt().disable();
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Add self Test to the alarm class
	 */
	public static void alarmTest1() {
		int durations[] = { 100 * 1000,1000, 10 * 1000,  };
		long t0, t1;
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
		
		KThread war = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(10*100000);
				System.out.println("warrior");
			}
		});
		KThread wiz = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(10*10);
				System.out.println("wizard");
			}
		});
		KThread thi = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(10*1000);
				System.out.println("thief");
			}
		});
		
		war.fork();
		wiz.fork();
		thi.fork();
		war.join();
		wiz.join();
		thi.join();
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking up the thread
	 * immediately (placing it in the scheduler ready set) and returning true. If
	 * <i>thread</i> has no timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().disable();
		for (Thread_with_time kth : Alarm.t_queue) {
			if(kth.thread == thread) {
				Machine.interrupt().restore(intStatus);
				thread.ready();
				return Alarm.t_queue.remove(kth);
			}
		}
		Machine.interrupt().restore(intStatus);
		return false;
	}

	/**
	 * Initialized data structure here
	 */
	class Thread_with_time {
		KThread thread;
		long waittime;

		Thread_with_time(KThread thread, long waittime) {
			this.thread = thread;
			this.waittime = waittime;
		}
	}
	
//	private static Queue<Thread_with_time> t_queue = new LinkedList<>();
	private static PriorityQueue<Thread_with_time> t_queue = new PriorityQueue<>(new Comparator<Thread_with_time>() {
		public int compare(Thread_with_time a, Thread_with_time b) {
			if (a.waittime-b.waittime < 0) return -1;
			else return 1;
		}
	});
//	private static PriorityQueue<Thread_with_time> t_queue = new PriorityQueue<>((a,b) -> (a.waittime - b.waittime>0?1:-1));

}
