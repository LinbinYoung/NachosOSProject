package nachos.vm;

import java.util.LinkedList;

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
		super.pageTable = new TranslationEntry[super.numPages];
		for (int i = 0; i < super.numPages; i++) {
			super.pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		}
		return true;
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
			if (vaddr >= numPages*pageSize){
				VMKernel.vmlock.release();
				return successRead;
			}
			int vpn = Processor.pageFromAddress(vaddr);
			int p_offset = Processor.offsetFromAddress(vaddr);
			int readLen = Math.min(pageSize - p_offset, length);
			if (this.pageTable[vpn].valid == false){
				handlePageFault(vaddr);
				VMKernel.FreeCounter ++;
				if (this.pageTable[vpn].valid == false){
					//not allocated
					VMKernel.FreeCounter --;
					VMKernel.vmlock.release();
					return successRead;
				}
			}else{
				VMKernel.FreeCounter ++;
			}
			// After getting the valid page in the pageFault
			// We have finish the mapping between pageTable and Memory
			// synchronized between Information.TLE and this.PageTable
			pageTable[vpn].used = true;
			VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].TLE = pageTable[vpn];
			VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = true;
			int paddr = pageTable[vpn].ppn * pageSize + p_offset;
			if (paddr < 0 || paddr >= memory.length) {
				//wake up a process that waits for this page frame
				VMKernel.FreeCounter --;
				VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin= false;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return successRead;
			}
			try {
				System.arraycopy(memory, paddr, data, offset, readLen);
			}catch(Exception e){
				//wake up a process that waits for this page frame
				VMKernel.FreeCounter --;
				VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = false;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return successRead;
			}
			VMKernel.FreeCounter --;
			VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = false;
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
		if (vaddr < 0){
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
				VMKernel.FreeCounter ++;
				if (!this.pageTable[vpn].valid) {
					// not allocated
					VMKernel.FreeCounter --;
					VMKernel.vmlock.release();
					return sucessWrite;
				}
			}else {
				VMKernel.FreeCounter ++;
			}
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;
			//synchronized between Information.TLE and this.PageTable
			VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].TLE = pageTable[vpn];
			VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = true;
			VMKernel.FreeCounter ++;
			int writeLen = Math.min(length, pageSize-p_offset);
			int paddr = this.pageTable[vpn].ppn * pageSize + p_offset;
			if (paddr < 0 || paddr >= memory.length) {
				VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = false;
				VMKernel.FreeCounter --;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return sucessWrite;
			}
			try {
				System.arraycopy(data, offset, memory, paddr, writeLen);
			}catch(Exception e){
				VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = false;
				VMKernel.FreeCounter --;
				VMKernel.CV.wake();
				VMKernel.vmlock.release();
				return sucessWrite;
			}
			VMKernel.FrameAttachedInfo[pageTable[vpn].ppn].pin = false;
			VMKernel.FreeCounter --;
			VMKernel.CV.wake();
			length -= writeLen;
			vaddr += writeLen;
			offset += writeLen;
			sucessWrite += writeLen;
		}
		VMKernel.vmlock.release();
		return sucessWrite;
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
	
	public void handlePageFault(int vaddr) {
		// The requested frame's corresponding vpn is cur_vpn
		// We first check the coff section then 8 stack page and then 1 parameter page
		UserKernel.lock_page.P();
		int goal_vpn = Processor.pageFromAddress(vaddr);
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			int start = section.getFirstVPN();
			Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = start + i;
				if (vpn == goal_vpn){
					TranslationEntry entry = pageTable[vpn];
					UserKernel.lock_page.P();
					Integer free_physical_page;
					try {
						free_physical_page = UserKernel.mPhyPage.removeFirst();
					}catch(IndexOutOfBoundsException e){
						// Clock Algorithm to fetch the physical frame that we want
						for(;;) {
							if (VMKernel.PinCounter == Machine.processor().getNumPhysPages()) {
								VMKernel.CV.sleep();
							}
							if (!VMKernel.FrameAttachedInfo[VMKernel.victim].pin) {
								// the victim page has not been pinned
								if (!VMKernel.FrameAttachedInfo[VMKernel.victim].TLE.used) {
									// we have found the goal, the corresponding physical frame is 
									// not used and is not pinned
									free_physical_page = VMKernel.victim;
									VMKernel.victim = (VMKernel.victim + 1) % (Machine.processor().getNumPhysPages()); // Do we really need it?
									break;
								}
							}// Pin here we should use integer or boolean
							VMKernel.victim = (VMKernel.victim + 1) % (Machine.processor().getNumPhysPages());
						}
						//check the dirty bit of the free_physical
						if (VMKernel.FrameAttachedInfo[free_physical_page].TLE.dirty) {
							// This page has been modified by another process
							// Here we use spn to index the page frame for the swap file
							Integer spn;
							try {
								spn = VMKernel.freeSwapPage.removeFirst();
							}catch(IndexOutOfBoundsException e_e) {
								//add a new page to the swap file
								spn = VMKernel.swap_page_num;
								VMKernel.swap_page_num ++;
							}
							// Need to store the info of current page to the swap file
							VMKernel.swap_file.write(spn*VMProcess.pageSize, Machine.processor().getMemory(), Processor.makeAddress(VMKernel.FrameAttachedInfo[free_physical_page].TLE.ppn,0), VMProcess.pageSize);
							VMKernel.FrameAttachedInfo[free_physical_page].TLE.vpn = spn;
							VMKernel.FrameAttachedInfo[free_physical_page].TLE.valid = false;
							// Map from swap file to actually memory, but the actual index is still vpn itself
						}
						if (pageTable[vpn].dirty) {
							VMKernel.swap_file.read(pageTable[vpn].vpn * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(free_physical_page, 0), Processor.pageSize);
                            VMKernel.freeSwapPage.add(pageTable[vpn].vpn);
                            pageTable[vpn] = new TranslationEntry(vpn, free_physical_page, true, false, true, true);
						}else {
							entry.ppn = free_physical_page;
							entry.readOnly = section.isReadOnly();
							section.loadPage(i, entry.ppn);
						}
					}// end for catch
					VMKernel.FrameAttachedInfo[entry.ppn].vmp = this;
		            VMKernel.FrameAttachedInfo[entry.ppn].TLE = pageTable[vpn];
					break;
				} // allocate the physical page that we need, end if
			} // end for
		}
		// 0-num_of_sections
		// 8 stack pages
		// 1 page reserved for arguments
		for (int i = numPages - 9; i < numPages; i++) {
			int vpn = i;
			if (vpn == goal_vpn){
				TranslationEntry entry = pageTable[vpn];
				UserKernel.lock_page.P();
				Integer free_physical_page;
				try {
					free_physical_page = UserKernel.mPhyPage.removeFirst();
				}catch(IndexOutOfBoundsException e){
					// Clock Algorithm to fetch the physical frame that we want
					for(;;) {
						if (VMKernel.PinCounter == Machine.processor().getNumPhysPages()) {
							VMKernel.CV.sleep();
						}
						if (!VMKernel.FrameAttachedInfo[VMKernel.victim].pin) {
							// the victim page has not been pinned
							if (!VMKernel.FrameAttachedInfo[VMKernel.victim].TLE.used) {
								// we have found the goal, the corresponding physical frame is 
								// not used and is not pinned
								free_physical_page = VMKernel.victim;
								VMKernel.victim = (VMKernel.victim + 1) % (Machine.processor().getNumPhysPages()); // Do we really need it?
								break;
							}
						}// Pin here we should use integer or boolean
						VMKernel.victim = (VMKernel.victim + 1) % (Machine.processor().getNumPhysPages());
					}
					//check the dirty bit of the free_physical
					if (VMKernel.FrameAttachedInfo[free_physical_page].TLE.dirty) {
						// This page has been modified by another process
						// Here we use spn to index the page frame for the swap file
						Integer spn;
						try {
							spn = VMKernel.freeSwapPage.removeFirst();
						}catch(IndexOutOfBoundsException e_e) {
							//add a new page to the swap file
							spn = VMKernel.swap_page_num;
							VMKernel.swap_page_num ++;
						}
						// Need to store the info of current page to the swap file
						VMKernel.swap_file.write(spn*VMProcess.pageSize, Machine.processor().getMemory(), Processor.makeAddress(VMKernel.FrameAttachedInfo[free_physical_page].TLE.ppn,0), VMProcess.pageSize);
						VMKernel.FrameAttachedInfo[free_physical_page].TLE.vpn = spn;
						VMKernel.FrameAttachedInfo[free_physical_page].TLE.valid = false;
						// Map from swap file to actually memory, but the actual index is still vpn itself
					}
					if (pageTable[vpn].dirty) {
						VMKernel.swap_file.read(pageTable[vpn].vpn * Processor.pageSize, Machine.processor().getMemory(), Processor.makeAddress(free_physical_page, 0), Processor.pageSize);
                        VMKernel.freeSwapPage.add(pageTable[vpn].vpn);
                        pageTable[vpn] = new TranslationEntry(vpn, free_physical_page, true, false, true, true);
					}else {
						entry.ppn = free_physical_page;
					}
				}// end for catch
				VMKernel.FrameAttachedInfo[entry.ppn].vmp = this;
	            VMKernel.FrameAttachedInfo[entry.ppn].TLE = pageTable[vpn];
				break;
			} // allocate the physical page that we need, end if
		} // end for
		UserKernel.lock_page.V();
	}
	
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		switch (cause) {
		case Processor.exceptionPageFault:
			handlePageFault(processor.readRegister(Processor.regBadVAddr));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
}
