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
		this.pageTable = new TranslationEntry[numPages];
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

//	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		VMKernel.vmmutex.acquire();
//		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
//		if (data == null || vaddr < 0) {
//			VMKernel.vmmutex.release();
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
//				VMKernel.vmmutex.release();
//				return sucessTransfer;
//			}
//
//			// get the vpn
//			int vpn = Processor.pageFromAddress(vaddr);
//			if (vpn < 0 && vpn > this.pageTable.length) {
//				VMKernel.vmmutex.release();
//				return sucessTransfer;
//			}
//
//			if (this.pageTable[vpn].valid == false) {
//				this.handlePageFault(vaddr);
//				if (this.pageTable[vpn].valid == false)
//					VMKernel.vmmutex.release();
//				return sucessTransfer;
//			}
//
//			// the TE entry is valid // Get the physical address
//			int ppn = this.pageTable[vpn].ppn;
//			int p_offset = Processor.offsetFromAddress(vaddr);
//			int paddr = ppn * this.pageSize + p_offset;
//
//			if (paddr < 0 || paddr >= memory.length) {
//				VMKernel.vmmutex.release();
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
//		VMKernel.vmmutex.release();
//		return sucessTransfer;
//	}
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		VMKernel.vmmutex.acquire();
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0) {
			VMKernel.vmmutex.release();
			return 0;
		}
		int left = length;
		int amount = 0;
		int cur_offset = offset;
		int total_read = 0;
		int paddr = -1;
		int paddr_offset = Processor.offsetFromAddress(vaddr);
		int vpn = Processor.pageFromAddress(vaddr);

		if (vpn >= pageTable.length || vpn < 0) {
			VMKernel.vmmutex.release();
			return total_read;
		}
		if (pageTable[vpn].valid) {
			VMKernel.IPT[pageTable[vpn].ppn].pin = true;
			VMKernel.pinCount++;
			pageTable[vpn].used = true;
			paddr = pageTable[vpn].ppn * pageSize + paddr_offset; // if paddr but not good used bit set?????
		} else {
			handlePageFault(vaddr); // an error may occur??????
			if (pageTable[vpn].valid) {
				VMKernel.IPT[pageTable[vpn].ppn].pin = true;
				VMKernel.pinCount++;
				pageTable[vpn].used = true;
				paddr = pageTable[vpn].ppn * pageSize + paddr_offset;
			} else {
				VMKernel.vmmutex.release();
				return total_read;
			}
		}
// for now, just assume that virtual addresses equal physical addresses
		if (paddr < 0 || paddr >= memory.length) {
			VMKernel.IPT[pageTable[vpn].ppn].pin = false;
			VMKernel.pinCount--;
			VMKernel.CV.wake();
			VMKernel.vmmutex.release();
			return 0;
		}

		amount = Math.min(left, (pageSize - paddr_offset));
		System.arraycopy(memory, paddr, data, offset, amount);
		VMKernel.IPT[pageTable[vpn].ppn].pin = false;
		VMKernel.pinCount--;
		VMKernel.CV.wake();
		total_read += amount;
		cur_offset += amount;
		left -= amount;
		while (left > 0) {
			vpn++;
			if (vpn >= pageTable.length || vpn < 0) {
				VMKernel.vmmutex.release();
				return total_read;
			}
			if (pageTable[vpn].valid) {
				VMKernel.IPT[pageTable[vpn].ppn].pin = true;
				VMKernel.pinCount++;
//System.out.println("b");
				pageTable[vpn].used = true;
				paddr = pageTable[vpn].ppn * pageSize;
			} else {
				vaddr = Processor.makeAddress(vpn, 0);
				handlePageFault(vaddr); // an error may occurrrrrr?????
				if (pageTable[vpn].valid) { // valid means correct?????
					VMKernel.IPT[pageTable[vpn].ppn].pin = true;
					VMKernel.pinCount++;
//System.out.println("a");
					pageTable[vpn].used = true;
					paddr = pageTable[vpn].ppn * pageSize;
				} else {
					VMKernel.vmmutex.release();
					return total_read; // else return immedia????????
				}
			}

			if (paddr < 0 || paddr >= memory.length) {
				VMKernel.IPT[pageTable[vpn].ppn].pin = false;
				VMKernel.pinCount--;
				VMKernel.CV.wake();
				VMKernel.vmmutex.release();
				return total_read;
			}
			amount = Math.min(left, pageSize);
			System.arraycopy(memory, paddr, data, cur_offset, amount);
			VMKernel.IPT[pageTable[vpn].ppn].pin = false;
			VMKernel.pinCount--;
//System.out.println("jkafahkjfhadjkfhdashgasfbvsdfbasdfasd");
			VMKernel.CV.wake();
			total_read += amount;
			cur_offset += amount;
			left -= amount;
		}

		VMKernel.vmmutex.release();
		return total_read;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		VMKernel.vmmutex.acquire();

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0) {
			VMKernel.vmmutex.release();
			return 0;
		}
		int left = length;
		int amount = 0;
		int cur_offset = offset;
		int total_write = 0;
		int paddr = -1;
		int paddr_offset = Processor.offsetFromAddress(vaddr);
		int vpn = Processor.pageFromAddress(vaddr);

		if (vpn >= pageTable.length || vpn < 0) {
			VMKernel.vmmutex.release();
			return total_write;
		}

		if (pageTable[vpn].valid) {
			VMKernel.IPT[pageTable[vpn].ppn].pin = true;
			VMKernel.pinCount++;
//System.out.println("c");
			if (pageTable[vpn].readOnly == false) {
				paddr = pageTable[vpn].ppn * pageSize + paddr_offset;
				pageTable[vpn].used = true;
			}
		} else {
			handlePageFault(vaddr); // an error may occur??????
			if (pageTable[vpn].valid) {
				if (pageTable[vpn].readOnly == false) {
					VMKernel.IPT[pageTable[vpn].ppn].pin = true;
					VMKernel.pinCount++;
//System.out.println("d");
					paddr = pageTable[vpn].ppn * pageSize + paddr_offset;
					pageTable[vpn].used = true;
				} else {
					VMKernel.vmmutex.release();
					return total_write;
				}
			} else {
				VMKernel.vmmutex.release();
				return total_write;
			}
		}

		// for now, just assume that virtual addresses equal physical addresses
		if (paddr < 0 || paddr >= memory.length) {
			VMKernel.IPT[pageTable[vpn].ppn].pin = false;
			VMKernel.pinCount--;
			VMKernel.CV.wake();
			VMKernel.vmmutex.release();
			return 0;
		}

		amount = Math.min(left, (pageSize - paddr_offset));
		System.arraycopy(data, offset, memory, paddr, amount);
		if (amount > 0) {
			pageTable[vpn].dirty = true;
		}
		VMKernel.IPT[pageTable[vpn].ppn].pin = false;
		VMKernel.pinCount--;
		VMKernel.CV.wake();
		total_write += amount;
		cur_offset += amount;
		left -= amount;
		while (left > 0) {
			vpn++;
			if (vpn >= pageTable.length || vpn < 0) {
				VMKernel.vmmutex.release();
				return total_write;
			}
			if (pageTable[vpn].valid) {
				if (pageTable[vpn].readOnly == false) {
					VMKernel.IPT[pageTable[vpn].ppn].pin = true;
					VMKernel.pinCount++;
					paddr = pageTable[vpn].ppn * pageSize;
					pageTable[vpn].used = true;
				} else {
					VMKernel.vmmutex.release();
					return total_write;
				}
			} else {
				vaddr = Processor.makeAddress(vpn, 0);
				handlePageFault(vaddr); // an error may occur??????
				if (pageTable[vpn].valid) {
					if (pageTable[vpn].readOnly == false) {
						VMKernel.IPT[pageTable[vpn].ppn].pin = true;
						VMKernel.pinCount++;
						paddr = pageTable[vpn].ppn * pageSize;
						pageTable[vpn].used = true;
					} else {
						VMKernel.vmmutex.release();
						return total_write;
					}
				} else {
					VMKernel.vmmutex.release();
					return total_write;
				}
			}

			if (paddr < 0 || paddr >= memory.length) {
				VMKernel.IPT[pageTable[vpn].ppn].pin = false;
				VMKernel.pinCount--;
				VMKernel.CV.wake();
				VMKernel.vmmutex.release();
				return total_write;
			}
			amount = Math.min(left, pageSize);
			System.arraycopy(data, cur_offset, memory, paddr, amount);
			if (amount > 0) {
				pageTable[vpn].dirty = true;
			}
			VMKernel.IPT[pageTable[vpn].ppn].pin = false;
			VMKernel.pinCount--;
			VMKernel.CV.wake();
			total_write += amount;
			cur_offset += amount;
			left -= amount;
		}

		VMKernel.vmmutex.release();
		return total_write;

	}

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

		for (int i = coffVpn + 1; i < this.numPages; ++i) {
			int vpn = i;
			if (vpn == badVpn) {
				int next_ppn = (UserKernel.freePhyPages.isEmpty()) ? this.evictPage()
						: UserKernel.freePhyPages.removeFirst();

				if (!this.pageTable[vpn].dirty)
					this.fillZero(vpn, next_ppn);
				else
					this.handleDirtyPage(vpn, next_ppn);

				VMKernel.IPT[next_ppn].process = this;
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
				++VMKernel.victim;
				VMKernel.victim /= totalPhyPages;
				continue;
			}

			if (VMKernel.IPT[VMKernel.victim].entry.used == false)
				break;
			//VMKernel.IPT[VMKernel.victim].entry.used = false; // why????
			++VMKernel.victim;
			VMKernel.victim /= totalPhyPages;

		}

		int victimNum = VMKernel.victim;
		++VMKernel.victim;
		VMKernel.victim %= totalPhyPages;

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
		//int ppn = VMKernel.IPT[victimNum].entry.ppn;
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
		// Notice the dirty bit!
		pageTable[vpn] = new TranslationEntry(vpn, next_ppn, true, false, true, true);

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
