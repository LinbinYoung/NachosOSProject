package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel(){
		super();
		//initialized FramesInfo
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		this.freeSwapPage = new LinkedList<>();
		this.vmlock = new Lock();
		this.victim = 0;
		this.PinCounter = 0;
		this.FreeCounter = 0;
		this.swap_page_num = 0;
		this.swap_file = new OpenFile();
		this.CV = new Condition(vmlock);
		for (int i = 0; i < Machine.processor().getNumPhysPages(); i ++) {
			FrameAttachedInfo[i] = new FramesInfo(null, null, false);
		}
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;
	private static final char dbgVM = 'v';
	public static LinkedList<Integer> freeSwapPage;
	public static int victim;
	public static int PinCounter;
	public static int FreeCounter;
	public static int swap_page_num;
	public static Lock vmlock;
	public static Condition CV;
	public static OpenFile swap_file;
	public static FramesInfo[] FrameAttachedInfo = new FramesInfo[Machine.processor().getNumPhysPages()];
}

class FramesInfo{
	VMProcess vmp;
	TranslationEntry TLE;
	boolean pin;
	FramesInfo(VMProcess vmp, TranslationEntry TLE, boolean pin){
		this.vmp = vmp;
		this.TLE = TLE;
		this.pin = pin;
	}
}
