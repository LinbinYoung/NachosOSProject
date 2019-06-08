package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.Arrays;

/**
 * write1, write4
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
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
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
		for(int i = 0; i<super.numPages; i++){
			super.pageTable[i] = new TranslationEntry(i, -1, false, false, false,false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	//TODO:  Page Pinning. At times it will be necessary to "pin" a page in memory,
	// making it temporarily impossible to evict.
	// Update readVirtualMemory and writeVirtualMemory to handle invalid pages and page faults.
	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		System.out.printf("[write vm]: started, offset: %d, length: %d\n", offset, length);
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		VMKernel.pinLock.acquire();
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 ) { //TODO: this is not needed, but why || vaddr >= memory.length
			VMKernel.pinLock.release();
			return 0;
		}
		int vpn = Processor.pageFromAddress(vaddr);
		int off = Processor.offsetFromAddress(vaddr);
		System.out.printf("[write vm]: w5, vpn: %d, off: %d\n", vpn, off);
		int total_write = 0;
		int paddr = pageTable[vpn].ppn * pageSize + off;
		int write = 0;
//		length = Math.min(length, numPages * pageSize - vaddr);
		while(length > 0){
//			if(!pageTable[vpn].valid) break;
			if(paddr < 0 || paddr >= memory.length) {  //vpn >= super.pageTable.length ||
				VMKernel.pinLock.release();
				return total_write;
			}
			System.out.printf("[write vm]: w1, vpn: %d, possible spn: %d, vaddr: %d, ppn: %d, valid: %s\n",
					vpn, pageTable[vpn].vpn, vaddr, pageTable[vpn].ppn, pageTable[vpn].valid);
			if(!pageTable[vpn].valid) handlePageFault(vaddr);
			System.out.printf("[write vm]: w2, vpn: %d, possible spn: %d, vaddr: %d, ppn: %d, valid: %s\n",
					vpn, pageTable[vpn].vpn, vaddr, pageTable[vpn].ppn, pageTable[vpn].valid);
			if(pageTable[vpn].valid){
				//if vaddr in read only section, return ???
				if (pageTable[vpn].readOnly) return total_write;
				VMKernel.IPT[pageTable[vpn].ppn].pinCount++;
				VMKernel.totalPinCount++;
				pageTable[vpn].used = true;
				pageTable[vpn].dirty = true;
			}else {
				VMKernel.pinLock.release();
				System.out.printf("[write vm]: w3, vpn: %d, possible spn: %d, vaddr: %d, ppn: %d, valid: %s\n",
						vpn, pageTable[vpn].vpn, vaddr, pageTable[vpn].ppn, pageTable[vpn].valid);
				return total_write;
			}
			write = Math.min(length, pageSize-off);
			paddr = pageTable[vpn].ppn * pageSize + off;
			System.arraycopy(data, offset, memory, paddr, write);
			System.out.println(Arrays.toString(Arrays.copyOfRange(memory, paddr, paddr+write)));
			System.out.println(Arrays.toString(Arrays.copyOfRange(data, offset, offset+write)));
			System.out.printf("[write vm]: w4, vpn: %d, write: %d, length: %d, pageSize: %d, offset: %d\n",vpn, write, length, pageSize, off);
			off = 0;
			total_write += write;
			offset += write;
			vaddr += write;
			length -= write;
			VMKernel.IPT[pageTable[vpn].ppn].pinCount--;
			VMKernel.totalPinCount--;
			VMKernel.pinCond.wake();
			vpn++;
//			creating binary.out...
//...passed (fd = 2)
//		writing 4096 bytes to file, 4096 bytes at a time...
//...passed (wrote 4096 bytes)
//			closing 2...
//			opening binary.out...
//...passed (fd = 2)
//			reading binary.out into buffer...
		}
		VMKernel.pinLock.release();
		return total_write;
	}

	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		System.out.printf("[Read VM]: vaddr <%d>, offset <%d>, length <%d>\n", vaddr, offset, length);
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= memory.length) return 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int off = Processor.offsetFromAddress(vaddr);
		int first = offset;
		int paddr = 0;
		int read = 0;
//		length = Math.min(length, numPages*pageSize - vaddr);
		while(length > 0){
//			if (!pageTable[vpn].valid) break;
			if(vpn >= super.pageTable.length || paddr<0 || paddr >= memory.length) return offset-first;
			if(!pageTable[vpn].valid) handlePageFault(vaddr);
			if(pageTable[vpn].valid){
				//TODO: readonly not needed to check
//				if (pageTable[vpn].readOnly) {System.out.println("Read only"); return offset-first;}
				VMKernel.IPT[pageTable[vpn].ppn].pinCount++;
				VMKernel.totalPinCount++;
				pageTable[vpn].used = true;
			}else return offset - first;
			paddr = pageTable[vpn].ppn * pageSize + off;
			read = Math.min(length, pageSize - off);
			System.arraycopy(memory, paddr, data, offset, read);
			off = 0;
			offset += read;
			length -= read;
//			System.out.printf("[read VM]: vpn <%d>, ppn <%d>\n", vpn, pageTable[vpn].ppn);
			VMKernel.IPT[pageTable[vpn].ppn].pinCount--;
			VMKernel.totalPinCount--;
			VMKernel.pinLock.acquire();
			VMKernel.pinCond.wake();
			VMKernel.pinLock.release();
			vpn++;
		}
		return offset - first;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		switch (cause) {
			case Processor.exceptionPageFault:
//				System.out.printf("[handle ex]: bad vaddr <%d>\n",processor.readRegister(processor.regBadVAddr));
				this.handlePageFault(processor.readRegister(processor.regBadVAddr));
				break;
		default:
			super.handleException(cause);
			break;
		}
	}

	//Read in the contents of the faulted page either from the executable file or from swap (see below).
	//A fault on a code page should read the corresponding code page from the file, a fault on a data page should
	// read the corresponding data page from the file, and a fault on a stack page or arguments page should
	// zero-fill the frame.
	public void handlePageFault(int badVaddr) {
//		System.out.println("[handlePageFault]: ");
		int badVPN = Processor.pageFromAddress(badVaddr);
		Processor processor = Machine.processor();
		//Handle page fault by where the faulting address is in:
		// coding segment, then you will be loading a code page
		// data segment, then load the appropriate data page;
		// any other page, zero-fill it.
		// It is fine to loop through the sections of the COFF file until you find the appropriate section and page to
		// use (assuming it is in the COFF file).
		UserKernel.physicalPageSema.P();
		int ppn = -1, swapVPN = -1;
		if (!UserKernel.physicalPageList.isEmpty()) {
			ppn = UserKernel.physicalPageList.remove(0);
			UserKernel.physicalPageSema.V();
		} else {
			UserKernel.physicalPageSema.V();
			while (true) {
				while (VMKernel.totalPinCount == Machine.processor().getNumPhysPages()) {
					VMKernel.pinCond.sleep();
				}
				if (VMKernel.IPT[VMKernel.clock].tle.used) {
					VMKernel.IPT[VMKernel.clock].tle.used = false;
				} else {
					if (VMKernel.IPT[VMKernel.clock].pinCount == 0) {
						//Evict victim
						ppn = VMKernel.clock;
						VMKernel.clock = (VMKernel.clock + 1) % Machine.processor().getNumPhysPages();
						break;
					}
				}
				VMKernel.clock = (VMKernel.clock + 1) % Machine.processor().getNumPhysPages();
			}
			if (VMKernel.IPT[VMKernel.clock].tle.dirty) {
				int spn = VMKernel.freeSwapPages.isEmpty() ? VMKernel.numSwapPages++ : VMKernel.freeSwapPages.poll();
				VMKernel.swapFile.write(Processor.makeAddress(spn, 0), Machine.processor().getMemory(),
						Processor.makeAddress(ppn, 0), Processor.pageSize);
				VMKernel.IPT[ppn].tle.vpn = spn;
			}
			VMKernel.IPT[ppn].tle.valid = false;
//			ppn = VMKernel.IPT[badVPN].tle.ppn;
		}
//		System.out.printf("[handle fault]: vpn <%d>, ppn <%d>, bad vaddr <%d>\n", badVPN, ppn, badVaddr);
		if (super.pageTable[badVPN].dirty){
			VMKernel.swapFile.read(Processor.makeAddress(pageTable[badVPN].vpn, 0),
					Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), Processor.pageSize);
			VMKernel.freeSwapPages.offer(super.pageTable[badVPN].vpn);
			super.pageTable[badVPN] = new TranslationEntry(badVPN, ppn, true, false, true, true);
			if(badVaddr==8448) {
				System.out.printf("[handle page fault]: h1, vpn: %d, possible spn: %d, vaddr: %d, ppn: %d, valid: %s\n", badVPN,
						pageTable[badVPN].vpn, badVaddr, pageTable[badVPN].ppn, pageTable[badVPN].valid);
			}
		}else if (badVPN < numPages - stackPages){
			if(badVaddr==8448) System.out.println("[handle page fault]: h2");
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);
				Lib.debug(dbgProcess, "\tinitializing " + section.getName()
						+ " section (" + section.getLength() + " pages)");
				if (badVPN >= section.getFirstVPN() && badVPN < section.getFirstVPN() + section.getLength()) {
					section.loadPage(badVPN - section.getFirstVPN(), ppn);
					super.pageTable[badVPN] = new TranslationEntry(badVPN, ppn, true, section.isReadOnly(),
							true, false);
					break;
				}
			}
		}else{
			System.out.println("[handle fault]: vpn is not coff sections");
			byte[] data = new byte[Processor.pageSize];
			System.arraycopy(data, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0),
					Processor.pageSize);
			super.pageTable[badVPN] = new TranslationEntry(badVPN, ppn, true, false, true, false);
		}
		VMKernel.IPT[ppn].process = this;
		VMKernel.IPT[ppn].tle = super.pageTable[badVPN];
		if(badVaddr==8448) {
			System.out.printf("[handle page fault]: h5, vpn: %d, possible spn: %d, vaddr: %d, ppn: %d, valid: %s\n", badVPN,
					pageTable[badVPN].vpn, badVaddr, pageTable[badVPN].ppn, pageTable[badVPN].valid);
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
