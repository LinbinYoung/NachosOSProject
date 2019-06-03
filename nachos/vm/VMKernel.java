package nachos.vm;

import java.util.*;
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
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		clock = 0;
		clockSema = new Semaphore(1);

		swapFile = fileSystem.open(swapFileName, true);
		numSwapPages = 0;
		freeSwapPages = new PriorityQueue<>((a, b) -> a-b);

		IPT = new InvertedPageFrame[Machine.processor().getNumPhysPages()];
		for(int i = 0; i<Machine.processor().getNumPhysPages(); i++){
			IPT[i] = new InvertedPageFrame(null, null, 0);
		}

		totalPinCount = 0;
		pinLock = new Lock();
		pinCond = new Condition(pinLock);
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
		//Delete the swap file on VMKernel Termination
		//○ ThreadedKernel.filesystem.remove(swapFileName)
		swapFile.close();
		fileSystem.remove(swapFileName);
		super.terminate();
	}

	/** mcip: page out an existing page
	 * Swap File. To manage swapped out pages on disk, use the StubFileSystem (via ThreadedKernel.fileSystem )
	 * */
//	public static void replacePage (TranslationEntry toAssignPage){
//		if (!clockIter.hasNext()) clockIter = clock.listIterator();
//		clockSema.P();
//		TranslationEntry toFreePage;
//		//TODO: iterates while it has been used? If a page is readOnly or pinned
//		while((toFreePage = clockIter.next()).used){
//			if (!clockIter.hasNext()) clockIter = clock.listIterator();
//		}
//		toFreePage.valid = false;
//		clockSema.V();
//		clockIter.set(toAssignPage);
//		toAssignPage.ppn = toFreePage.ppn;
//		toAssignPage.valid = true;
//		//we swap out a page when a page to be evicted is dirty
//		if(toFreePage.dirty){
//			byte[] buffer = new byte[Processor.pageSize];
//			System.arraycopy(Machine.processor().getMemory(), toFreePage.ppn*Processor.pageSize,
//					buffer, 0, Processor.pageSize);
//		}
//	}

	//mcip
	protected static int clock;
	protected static Semaphore clockSema;
	//When designing the swap file, keep in mind that the units of swap are pages. Thus you should try to
	// conserve disk space using the same techniques applied in virtual memory: if you end up having gaps
	// in your swap space (which will necessarily occur with a global swap file upon program termination),
	// try to fill them. As with physical memory in project 2, a global free list works well. You can assume
	// that the swap file can grow arbitrarily, and that there should not be any read/write errors. Assert
	// if there are.
	//Implement the swap file for storing pages evicted from physical memory. You will want to implement
	// methods to create a swap file, write pages from memory to swap (for page out), read from swap to
	// memory (for page in), etc.
	//When and where to create the swap file?
	//○ Create on VMKernel Initialization
	//○ Use the underlying file system
	//○ File = ThreadedKernel.filesystem.open(swapFileName)
	//Global free pages list can grow arbitrarily, follow the same idea
	//Record which places are available in the swap file
	// ○ Where can the physical page be?
	//  ■ memory or swap file
	// ○ How do we know if the physical page has been swapped out?
	//  ■ Valid bit
	//How to perform swap in/out?
	// ○ Simply read and write to the file
	// ○ File.read()/File.write()
	private static final String swapFileName = "VM_Swap.swap";
	protected static int numSwapPages;
	protected static OpenFile swapFile;
	protected static PriorityQueue<Integer> freeSwapPages;
	//Update PTE after swapping
	//How to know the page index or spn of a page in the swap file?
	// ○ Need to record relation between vpn and spn
	//  ■ Why not ppn?
	//  ■ Where to record relation?
	//which pages are pinned, and which process owns which pages.
	//Inverted Page Table
	//● Used to correlate physical page with process and page table entry info
	//● What should be the index?
	// ○ Physical page number
	//● Note: When updating a PTE, also update the IPT
	public class InvertedPageFrame{
		InvertedPageFrame(Process p, TranslationEntry tle, int pinCount){
			this.process = process;
			this.tle = tle;
			this.pinCount = pinCount;
		}
		public VMProcess process;
		public TranslationEntry tle;
		public int pinCount;
	}
	protected static InvertedPageFrame[] IPT;
	protected static int totalPinCount;
	protected static Lock pinLock;
	protected static Condition pinCond;

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

}
