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
		/*
		 * read data from memory and store the data into the array
		 */
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= memory.length) {
			return 0;
		}
		int success_read = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		if (vpn < 0 || vpn >= super.pageTable.length) {
			return 0; // success_read = 0
		}
		int phy_offset = Processor.offsetFromAddress(vaddr);
		TranslationEntry entry = super.pageTable[vpn];
		int phy_addr = pageSize * entry.ppn + phy_offset;
		if (phy_addr >= memory.length || phy_addr < 0) {
			System.out.println("Invalid Physical Address");
			return 0;
		}
		int start = 0;
		while (length > 0) {
			if (!entry.valid) {
				// trigger page default
				handlePageFault(Processor.makeAddress(vpn, 0));
			}
			entry.used = true;
			if (phy_offset + length > pageSize) {
				int can_write_length = pageSize - phy_offset;
				try {
					System.arraycopy(memory, phy_addr, data, start, can_write_length);
				} catch (Exception e) {
					return success_read;
				}
				start = start + can_write_length;
				length = length - can_write_length;
				success_read += can_write_length;
				if (++vpn > this.pageTable.length) {
					return success_read;
				}
				entry.used = false;
				entry = this.pageTable[vpn];
				phy_offset = 0;
				phy_addr = entry.ppn * pageSize + phy_offset;
				if (phy_addr >= memory.length || phy_addr < 0) {
					System.out.println("Invalid Physical Address");
					return success_read;
				}
				if (!entry.valid) {
					handlePageFault(Processor.makeAddress(vpn, 0));
				}
				entry.used = true;
			} else {
				int can_write_length = length;
				try {
					System.arraycopy(memory, phy_addr, data, start, can_write_length);
				} catch (Exception e) {
					return success_read;
				}
				success_read += can_write_length;
				start = start + can_write_length;
				length = length - can_write_length;
				entry.used = false;
			}
		} // end while
		return success_read;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		int success_write = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		if (vpn < 0 || vpn >= this.pageTable.length) {
			return 0;
		}
		int phy_offset = Processor.offsetFromAddress(vaddr); // Use vpn to get the entry of ppn
		TranslationEntry entry = this.pageTable[vpn];
		int phy_addr = pageSize * entry.ppn + phy_offset;
		if (phy_addr >= memory.length || phy_addr < 0) {
			System.out.println("Invalid Physical Address");
			return 0;
		}
		if (entry.readOnly) {
			// we cannot write data to memory
			return 0;
		}
		if (!entry.valid) {
			handlePageFault(Processor.makeAddress(vpn, 0));
		}
		entry.used = true;
		int start = 0;
		while (length > 0) {
			// write page by page
			if (phy_offset + length > pageSize) {
				int can_to_memory = pageSize - phy_offset;
				try {
					System.arraycopy(data, start, memory, phy_addr, can_to_memory);
				} catch (Exception e) {
					return success_write;
				}
				success_write += can_to_memory;
				start = start + can_to_memory;
				length = length - can_to_memory;
				if (++vpn > this.pageTable.length) {
					return success_write;
				}
				entry.used = false;
				// calculate the new phy_addr
				entry = this.pageTable[vpn];
				phy_offset = 0;
				phy_addr = pageSize * entry.ppn + phy_offset;
				if (phy_addr >= memory.length || phy_addr < 0) {
					System.out.println("Invalid Physical Address");
					return success_write;
				}
				if (entry.readOnly) {
					// we cannot write data to memory
					return success_write;
				}
				if (!entry.valid) {
					handlePageFault(Processor.makeAddress(vpn, 0));
				}
				entry.used = true;
			} else {
				int can_to_memory = length;
				try {
					System.arraycopy(data, start, memory, phy_addr, can_to_memory);
				} catch (Exception e) {
					return success_write;
				}
				success_write += can_to_memory;
				start = start + can_to_memory;
				length = length - can_to_memory;
				entry.used = false;
			}
		}
		return success_write;
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
		// the requested frame's corresponding vpn is cur_vpn
		// We first check the .coff section then 8 stack page and then 1 parameter page
		int goal_vpn = Processor.pageFromAddress(vaddr);
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			int start = section.getFirstVPN();
			Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = start + i;
				if (vpn == goal_vpn) {
					TranslationEntry entry = pageTable[vpn];
					UserKernel.lock_page.P();
					Integer free_physical_page = UserKernel.mPhyPage.removeFirst();
					UserKernel.lock_page.V();
					if (free_physical_page == null) {
						// keep pining until get available physical pages
						// victim page
						while (true) {
							if (VMKernel.PinCounter == Machine.processor().getNumPhysPages()) {
								// No available Physical Pages
								VMKernel.CV.sleep();
							}
							if (VMKernel.FrameAttachedInfo[VMKernel.victim].pin) {
								// the victim page has been referenced, jump to next available page
								VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
								continue;
							} else {
								if (VMKernel.FrameAttachedInfo[VMKernel.victim].TLE.used == false) {
									// find the available physical page
									break;
								}
								VMKernel.FrameAttachedInfo[VMKernel.victim].TLE.used = false;
								VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
							}
						} // end pinning
							// Now the victim stores the physical page number we try to allocate
						int evict_num = VMKernel.victim;
						VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
						// if the corresponding physical page is changed, dirty page = 1
						if (VMKernel.FrameAttachedInfo[evict_num].TLE.dirty) {
							// we could not use this page, because this page is modified by another page
							int spn = 0;
							if (VMKernel.freeSwapPage.isEmpty()) {
								// no available free Swap page
								spn = VMKernel.FreeCounter;
								VMKernel.FreeCounter++;
							}
							VMKernel.swap_file.write(spn * Processor.pageSize, Machine.processor().getMemory(),
									Processor.makeAddress(VMKernel.FrameAttachedInfo[evict_num].TLE.ppn, 0),
									Processor.pageSize);
							VMKernel.FrameAttachedInfo[evict_num].TLE.vpn = spn;
						}
						free_physical_page = evict_num; // note here is there any inaccuracy?
					}
					if (!pageTable[vpn].dirty) {
						entry.ppn = free_physical_page;
						entry.readOnly = section.isReadOnly();
						entry.valid = true;
						section.loadPage(i, entry.ppn);
					} else {
						VMKernel.swap_file.read(pageTable[vpn].vpn * Processor.pageSize,
								Machine.processor().getMemory(), Processor.makeAddress(free_physical_page, 0),
								Processor.pageSize);
						VMKernel.freeSwapPage.add(vpn);
						entry.ppn = free_physical_page;
						entry.readOnly = section.isReadOnly();
						entry.valid = true;
						section.loadPage(i, entry.ppn);
					}
//					VMKernel.FrameAttachedInfo[free_physical_page].TLE.valid = false;
					VMKernel.FrameAttachedInfo[free_physical_page].vmp = this;
					break;
				} // allocate the physical page that we need
			} // end for
		}

		// 0-num_of_sections
		// 8 stack pages
		// 1 page reserved for arguments
		for (int i = numPages - 9; i < numPages; i++) {
			int vpn = i;
			if (vpn == goal_vpn) {
				TranslationEntry entry = pageTable[vpn];
				UserKernel.lock_page.P();
				Integer free_physical_page = UserKernel.mPhyPage.removeFirst();
				UserKernel.lock_page.V();
				if (free_physical_page == null) {
					// keep pining until get available physical pages
					// victim page
					while (true) {
						if (VMKernel.PinCounter == Machine.processor().getNumPhysPages()) {
							// No available Physical Pages
							VMKernel.CV.sleep();
						}
						if (VMKernel.FrameAttachedInfo[VMKernel.victim].pin) {
							// the victim page has been referenced, jump to next available page
							VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
							continue;
						} else {
							if (VMKernel.FrameAttachedInfo[VMKernel.victim].TLE.used == false) {
								// find the available physical page
								break;
							}
							VMKernel.FrameAttachedInfo[VMKernel.victim].TLE.used = false;
							VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
						}
					} // end pinning
						// Now the victim stores the physical page number we try to allocate
					int evict_num = VMKernel.victim;
					VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
					// if the corresponding physical page is changed, dirty page = 1
					if (VMKernel.FrameAttachedInfo[evict_num].TLE.dirty) {
						// we could not use this page, because this page is modified by another page
						int spn = 0;
						if (VMKernel.freeSwapPage.isEmpty()) {
							// no available free Swap page
							spn = VMKernel.FreeCounter;
							VMKernel.FreeCounter++;
						}
						VMKernel.swap_file.write(spn * Processor.pageSize, Machine.processor().getMemory(),
								Processor.makeAddress(VMKernel.FrameAttachedInfo[evict_num].TLE.ppn, 0),
								Processor.pageSize);
						VMKernel.FrameAttachedInfo[evict_num].TLE.vpn = spn;
					}
					free_physical_page = evict_num; // note here is there any inaccuracy?
				}
				if (!pageTable[vpn].dirty) {
					entry.ppn = free_physical_page;
					entry.valid = true;
				} else {
					VMKernel.swap_file.read(pageTable[vpn].vpn * Processor.pageSize, Machine.processor().getMemory(),
							Processor.makeAddress(free_physical_page, 0), Processor.pageSize);
					VMKernel.freeSwapPage.add(vpn);
					entry.ppn = free_physical_page;
					entry.valid = true;
				}
//				VMKernel.FrameAttachedInfo[free_physical_page].TLE.valid = false;
				VMKernel.FrameAttachedInfo[free_physical_page].vmp = this;
				break;
			} // allocate the physical page that we need
		} // end for
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
