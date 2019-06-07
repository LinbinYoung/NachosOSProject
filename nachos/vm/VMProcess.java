package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch. Called by
	 * <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		// Part 1-1 initial all TEs as invalid
		this.pageTable = new TranslationEntry[this.numPages];
		for (int i = 0; i < numPages; i++) {
			this.pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		}
		// Part 1-2 Allocate physical page frames but don't load
		// Part 2-1 Don't allocate physical pages here!
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt> .
	 * The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionPageFault:
			// Part 1-3 Machine will trigger page fault exception
			// Part 1-4 Modify handle Exception to handle this!
			handlePageFault(processor.readRegister(Processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		VMKernel.vmlock.acquire();
		if (data == null || vaddr < 0) {
			VMKernel.vmlock.release();
			return 0;
		}
		byte[] memory = Machine.processor().getMemory();
		int successRead = 0;
		while (length > 0) {
			if (vaddr >= numPages * pageSize) {
				VMKernel.vmlock.release();
				return successRead;
			}
			int vpn = Processor.pageFromAddress(vaddr);
			int p_offset = Processor.offsetFromAddress(vaddr);
			int readLen = Math.min(pageSize - p_offset, length);
			if (this.pageTable[vpn].valid == false) {
				handlePageFault(vaddr);
				VMKernel.pinCount++;
				if (this.pageTable[vpn].valid == false) {
					// not allocated
					VMKernel.pinCount--;
					VMKernel.vmlock.release();
					return successRead;
				}
			} else {
				VMKernel.pinCount++;
			}
			// After getting the valid page in the pageFault
			// We have finish the mapping between pageTable and Memory
			// synchronized between Information.TLE and this.PageTable
			pageTable[vpn].used = true;
			VMKernel.IPT[pageTable[vpn].ppn].entry = pageTable[vpn];
			VMKernel.IPT[pageTable[vpn].ppn].pin = true;
			int paddr = pageTable[vpn].ppn * pageSize + p_offset;
			if (paddr < 0 || paddr >= memory.length) {
				// wake up a process that waits for this page frame
				VMKernel.pinCount--;
				VMKernel.IPT[pageTable[vpn].ppn].pin = false;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return successRead;
			}
			try {
				System.arraycopy(memory, paddr, data, offset, readLen);
			} catch (Exception e) {
				// wake up a process that waits for this page frame
				VMKernel.pinCount--;
				VMKernel.IPT[pageTable[vpn].ppn].pin = false;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return successRead;
			}
			VMKernel.pinCount--;
			VMKernel.IPT[pageTable[vpn].ppn].pin = false;
			VMKernel.CV.wake();
			vaddr += readLen;
			successRead += readLen;
			length -= readLen;
			offset += readLen;
		}
		VMKernel.vmlock.release();
		return successRead;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		VMKernel.vmlock.acquire();
		if (vaddr < 0) {
			VMKernel.vmlock.release();
			return 0;
		}
		byte[] memory = Machine.processor().getMemory();
		int sucessWrite = 0;
		while (length > 0) {
			int vpn = Processor.pageFromAddress(vaddr);
			int p_offset = Processor.offsetFromAddress(vaddr);
			if (this.pageTable[vpn].readOnly) {
				VMKernel.vmlock.release();
				return sucessWrite;
			}
			if (!this.pageTable[vpn].valid) {
				handlePageFault(vaddr);
				VMKernel.pinCount++;
				if (!this.pageTable[vpn].valid) {
					// not allocated
					VMKernel.pinCount--;
					VMKernel.vmlock.release();
					return sucessWrite;
				}
			} else {
				VMKernel.pinCount++;
			}
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;
			// synchronized between Information.TLE and this.PageTable
			VMKernel.IPT[pageTable[vpn].ppn].entry = pageTable[vpn];
			VMKernel.IPT[pageTable[vpn].ppn].pin = true;
			VMKernel.pinCount++;
			int writeLen = Math.min(length, pageSize - p_offset);
			int paddr = this.pageTable[vpn].ppn * pageSize + p_offset;
			if (paddr < 0 || paddr >= memory.length) {
				VMKernel.IPT[pageTable[vpn].ppn].pin = false;
				VMKernel.pinCount--;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return sucessWrite;
			}
			try {
				System.arraycopy(data, offset, memory, paddr, writeLen);
			} catch (Exception e) {
				VMKernel.IPT[pageTable[vpn].ppn].pin = false;
				VMKernel.pinCount--;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return sucessWrite;
			}
			VMKernel.IPT[pageTable[vpn].ppn].pin = false;
			VMKernel.pinCount--;
			VMKernel.CV.wake();
			length -= writeLen;
			vaddr += writeLen;
			offset += writeLen;
			sucessWrite += writeLen;
		}
		VMKernel.vmlock.release();
		return sucessWrite;
	}

//	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		VMKernel.vmlock.acquire();
//		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
//		if (data == null || vaddr < 0) {
//			VMKernel.vmlock.release();
//			return 0;
//		}
//
//		byte[] memory = Machine.processor().getMemory();
//
//		int sucessTransfer = 0;
//
//		while (length > 0) {
//
//			if (vaddr >= this.numPages * this.pageSize) {
//				VMKernel.vmlock.release();
//				return sucessTransfer;
//			}
//
//			// get the vpn
//			int vpn = Processor.pageFromAddress(vaddr);
//			if (vpn < 0 && vpn > this.pageTable.length) {
//				VMKernel.vmlock.release();
//				return sucessTransfer;
//			}
//
//			if (this.pageTable[vpn].valid == false) {
//				this.handlePageFault(vaddr);
//				if (this.pageTable[vpn].valid == false)
//					VMKernel.vmlock.release();
//				return sucessTransfer;
//			}
//
//			// the TE entry is valid // Get the physical address
//			int ppn = this.pageTable[vpn].ppn;
//			int p_offset = Processor.offsetFromAddress(vaddr);
//			int paddr = ppn * this.pageSize + p_offset;
//
//			if (paddr < 0 || paddr >= memory.length) {
//				VMKernel.vmlock.release();
//				return sucessTransfer;
//			}
//
//			int readLen = Math.min(length, this.pageSize - p_offset);
//
//			// read data System.arraycopy(memory, paddr, data, offset, readLen);
//
//			this.pageTable[vpn].used = true;
//			vaddr += readLen;
//			sucessTransfer += readLen;
//			length -= readLen;
//			offset += readLen;
//		}
//
//		VMKernel.vmlock.release();
//		return sucessTransfer;
//	}

	protected void handlePageFault(int badVaddr) {

		UserKernel.mutex.acquire();

		// get vpn
		int badVpn = Processor.pageFromAddress(badVaddr);
		int coffVpn = 0;

		for (int s = 0; s < this.coff.getNumSections(); ++s) {

			CoffSection section = this.coff.getSection(s);
			for (int i = 0; i < section.getLength(); ++i) {
				int vpn = section.getFirstVPN() + i;
				coffVpn = vpn;

				if (vpn == badVpn) {
					// Clock algorithm
					int next_ppn = (UserKernel.freePhyPages.isEmpty()) ? this.evictPage()
							: UserKernel.freePhyPages.removeFirst();

					if (!this.pageTable[vpn].dirty) {
						section.loadPage(i, next_ppn); // load to memory
						boolean readOnly = section.isReadOnly();
						// the used bit should be false
						this.pageTable[vpn] = new TranslationEntry(vpn, next_ppn, true, readOnly, true, false);
					} else {
						this.handleDirtyPage(vpn, next_ppn);
					}
					VMKernel.IPT[next_ppn].process = this;
					VMKernel.IPT[next_ppn].entry = this.pageTable[vpn];
				}
			}
		}

		Lib.assertTrue(coffVpn + 1 == this.numPages - 9);

		for (int i = this.numPages - 9; i < this.numPages; ++i) {

			int vpn = i;
			if (vpn == badVpn) {
				int next_ppn = (UserKernel.freePhyPages.isEmpty()) ? this.evictPage()
						: UserKernel.freePhyPages.removeFirst();

				if (!this.pageTable[vpn].dirty)
					this.fillZero(vpn, next_ppn);
				else
					this.handleDirtyPage(vpn, next_ppn);

				VMKernel.IPT[next_ppn].process = this;
				// sync IPT and pageTable
				VMKernel.IPT[next_ppn].entry = pageTable[vpn];
			}

		}

		UserKernel.mutex.release();

	}

	/*
	 * No free memory, need to evict a page Select a victim for replacement; BY
	 * Clock Algorithm. It will return ppn
	 */
	private int evictPage() {

		int totalPhyPages = Machine.processor().getNumPhysPages();
		while (true) {

			if (VMKernel.IPT[VMKernel.victim].pin == true) {
				if (VMKernel.pinCount == totalPhyPages)
					VMKernel.CV.sleep();

				VMKernel.victim = (VMKernel.victim + 1) % totalPhyPages;
				continue;
			}

			if (VMKernel.IPT[VMKernel.victim].entry.used == false)
				break;

			// for termination
			VMKernel.IPT[VMKernel.victim].entry.used = false;
			VMKernel.victim = (VMKernel.victim + 1) % totalPhyPages;

		}

		int victimNum = VMKernel.victim;
		VMKernel.victim = (VMKernel.victim + 1) % totalPhyPages;

		// Check if victim page is dirty or not?
		if (VMKernel.IPT[victimNum].entry.dirty) {

			// write it to swap
			int spn = (!VMKernel.freeSwapPages.isEmpty()) ? VMKernel.freeSwapPages.removeFirst() : VMKernel.num_sp++;

			int pos = spn * this.pageSize;
			byte[] buf = Machine.processor().getMemory();
			int off = Processor.makeAddress(VMKernel.IPT[victimNum].entry.ppn, 0);

			VMKernel.swapFile.write(pos, buf, off, this.pageSize);
			VMKernel.IPT[victimNum].entry.vpn = spn; // for the process using this page before
		}

		// The victimNum is the ppn
		VMKernel.IPT[victimNum].entry.valid = false; // process A -> swap file, no longer valid
		return victimNum;
	}

	/*
	 * The TE is dirty, We nedd to swap page in
	 */
	private void handleDirtyPage(int vpn, int next_ppn) {

		int pos = this.pageTable[vpn].vpn * Processor.pageSize;
		byte[] buf = Machine.processor().getMemory();
		int off = Processor.makeAddress(next_ppn, 0);
		// from file to memory
		VMKernel.swapFile.read(pos, buf, off, Processor.pageSize);
		VMKernel.freeSwapPages.add(pageTable[vpn].vpn);
		// Notice the dirty bit!!
		this.pageTable[vpn] = new TranslationEntry(vpn, next_ppn, true, false, true, false);

	}

	private void fillZero(int vpn, int ppn) {

		int pageSize = Processor.pageSize;
		byte[] data = new byte[pageSize];
		for (int i = 0; i < data.length; ++i)
			data[i] = 0;
		byte[] dest = Machine.processor().getMemory();
		int destPos = Processor.makeAddress(ppn, 0);
		System.arraycopy(data, 0, dest, destPos, pageSize);
		this.pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, false);
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
