package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */

public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		this.file_arr = new OpenFile[16];
		this.file_arr[0] = UserKernel.console.openForReading();
		this.file_arr[1] = UserKernel.console.openForWriting();
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		/**
		 * Allocate a new translation entry with the specified initial state.
		 * 
		 * @param vpn the virtual page number.
		 * @param ppn the physical page number.
		 * @param valid the valid bit.
		 * @param readOnly the read-only bit.
		 * @param used the used bit.
		 * @param dirty the dirty bit.
		 */
		for (int i = 0; i < numPhysPages; i++) {
			TranslationEntry instan = new TranslationEntry(i, i, false, false, false, false);
			pageTable[i] = instan;
		}
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */

	public static UserProcess newUserProcess() {
	    String name = Machine.getProcessClassName ();
	    
		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.
	    
		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		thread = new UThread(this);
		thread.setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);
		byte[] bytes = new byte[maxLength + 1];
		int bytesRead = readVirtualMemory(vaddr, bytes);
//		System.out.println(bytesRead+"LINBINYANG");
		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}
		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			return 0;
		}
		int success_read = 0;
		//Get vpn from vaddr
		int vpn = Processor.pageFromAddress(vaddr);
		int phy_offset = Processor.offsetFromAddress(vaddr);
		//Use vpn to get the entry of ppn
		TranslationEntry entry = this.pageTable[vpn];
		int phy_addr = pageSize*entry.ppn + phy_offset;
		if (phy_addr >= memory.length || phy_addr < 0) {
			System.out.println("Invalid Physical Address");
			return 0;
		}
//		System.out.println(entry.valid);
//		System.out.println("MICHAEL");
//		if (entry.valid) {
			// we can not ignore this page
			// read data page by page
			// length denotes how many bytes that yield to write
			// The pages are not continuous
			entry.used = true;
			int start = 0;
			while (length > 0) {
				if (phy_offset + length > pageSize) {
					int can_write_length = pageSize - phy_offset;
					System.arraycopy(memory, phy_addr, data, start, can_write_length);
					start = start + can_write_length;
					length = length - can_write_length;
					success_read += can_write_length;
					//update to the next physical address
					if (++vpn > this.pageTable.length) {
						return success_read;
					}
					entry.used = false;
					entry = this.pageTable[vpn];
					if (!entry.valid) {
						return success_read;
					}
					phy_offset = 0;
					phy_addr = entry.ppn*pageSize + phy_offset;
					if (phy_addr >= memory.length || phy_addr < 0) {
						System.out.println("Invalid Physical Address");
						return success_read;
					}
					entry.used = true;
				}else {
					int can_write_length = length;
					System.arraycopy(memory, phy_addr, data, start, can_write_length);
					success_read += can_write_length;
					System.out.println(success_read);
					start = start + can_write_length;
					length = length - can_write_length;
					entry.used = false;
				}
			}//end while
//		}
//		System.out.println("READ");
//		System.out.println(success_read);
		return success_read;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		int success_write = 0;
		//Get vpn from vaddr
		int vpn = Processor.pageFromAddress(vaddr);
		int phy_offset = Processor.offsetFromAddress(vaddr);
		//Use vpn to get the entry of ppn
		TranslationEntry entry = this.pageTable[vpn];
		int phy_addr = pageSize*entry.ppn + phy_offset;
		if (phy_addr >= memory.length || phy_addr < 0) {
			System.out.println("Invalid Physical Address");
			return 0;
		}
		if (entry.readOnly) {
			// we cannot write data to memory
			return 0;
		}
		entry.used = true;
		int start = 0;
		while (length > 0) {
			// write page by page
			if (phy_offset + length > pageSize) {
				int can_to_memory = pageSize - phy_offset;
				System.arraycopy(data, start, memory, phy_addr, can_to_memory);
				success_write += can_to_memory;
				start = start + can_to_memory;
				length = length - can_to_memory;
				if (++vpn > this.pageTable.length) {
					return success_write;
				}
				entry.used = false;
				//calculate the new phy_addr
				entry = this.pageTable[vpn];
				phy_offset = 0;
				phy_addr = pageSize*entry.ppn + phy_offset;
				if (phy_addr >= memory.length || phy_addr < 0) {
					System.out.println("Invalid Physical Address");
					return success_write;
				}
				if (entry.readOnly) {
					// we cannot write data to memory
					return success_write;
				}
				entry.used = true;
			}else {
				int can_to_memory = length;
				System.arraycopy(data, start, memory, phy_addr, can_to_memory);
				success_write += can_to_memory;
				start = start + can_to_memory;
				length = length - can_to_memory;
				entry.used = false;
			}
		}
		return success_write;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}
		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}
		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();
		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;
		// and finally reserve 1 page for arguments
		numPages++;
		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}
		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()){
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			int start = section.getFirstVPN();
			Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = start + i;
				// For now, just assume virtual addresses=physical addresses
				// Here we need to map the vpn to ppn
				// Get the ppn from the Pagetable
				TranslationEntry entry = pageTable[vpn];
				UserKernel.sem.P();
				Integer free_page = UserKernel.mPhyPage.removeFirst();
				UserKernel.sem.V();
				if (free_page == null) {
					// No available page
					unloadSections();
					return false;
				}else{
					/**
					 * Allocate a new translation entry with the specified initial state.
					 * @param vpn the virtual page number.
					 * @param ppn the physical page number.
					 * @param valid the valid bit.
					 * @param readOnly the read-only bit.
					 * @param used the used bit.
					 * @param dirty the dirty bit.
					 */
					entry.ppn = free_page;
					entry.readOnly = section.isReadOnly();
					entry.valid = true;
				}
				//load and run
				section.loadPage(i, entry.ppn);
			}
		}
			//0-num_of_coff sections
			//8 stack pages
			//1 page reserved for arguments
			for (int i = numPages - 9; i < numPages; i ++) {
				TranslationEntry entry = pageTable[i];
				//acquire lock, no race condition
				UserKernel.sem.P();
				Integer free_page = UserKernel.mPhyPage.removeFirst();
				//release lock
				UserKernel.sem.V();
				if (free_page == null) {
					// No available page
					unloadSections();
					return false;
				}else {
					/**
					 * Allocate a new translation entry with the specified initial state.
					 * @param vpn the virtual page number.
					 * @param ppn the physical page number.
					 * @param valid the valid bit.
					 * @param readOnly the read-only bit.
					 * @param used the used bit.
					 * @param dirty the dirty bit.
					 */
					entry.ppn = free_page;
					entry.valid = true;
				}
			}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// total number of entry for the process: numPages
		for(int i = 0; i < pageTable.length; i ++) {
			TranslationEntry entry = pageTable[i];
			if (entry.valid) {
				//valid false: we can ignore the page
				UserKernel.sem.P();
				UserKernel.mPhyPage.add(entry.ppn);
				UserKernel.sem.V();
			}
		}//end for
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();
		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);
		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);
		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		Machine.autoGrader().finishingCurrentProcess(status);
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
        // Do not remove this call to the autoGrader...
		// for now, unconditionally terminate with just one process
		Kernel.kernel.terminate();
		return 0;
	}

	private int handleOpen(int vaddr) {
		// Get the filename
		if (vaddr < 0) return -1;
		String get_filename = new UserProcess().readVirtualMemoryString(vaddr, 256);
		if (get_filename == null) {
			return -1;
		}
		
		for (int i = 0; i < this.file_arr.length; i ++) {
			if (this.file_arr[i] == null) {
				OpenFile instan = ThreadedKernel.fileSystem.open(get_filename, false);
				if (instan == null) return -1;
				this.file_arr[i] = instan;
				return i;
			}
		}
		return -1;
	}

	private int handleCreate(int vaddr) {
		// Get the filename
		String get_filename = new UserProcess().readVirtualMemoryString(vaddr, 256);
		if (get_filename == null) {
			return -1;
		}
		if (vaddr < 0) {
			return -1;
		}
		OpenFile instan = ThreadedKernel.fileSystem.open(get_filename, true);
		for (int i = 2; i < this.file_arr.length; i ++) {
			if (this.file_arr[i] != null) {
				//check if the file exists or not
				if (this.file_arr[i].getName().equals(get_filename)) {
					return -1;
				}
			}else {
				file_arr[i] = instan;
				return i;
			}
		}
		return -1;
	}

	private int handleRead(int fileDescriptor, int vaddr, int length) {
		if (fileDescriptor < 0 || fileDescriptor == 1 || fileDescriptor >= 16) {
			return -1;
		}
		if (length < 0 || vaddr < 0) {
			return -1;
		}
		OpenFile instan = this.file_arr[fileDescriptor];
		if (instan == null) {
			return -1;
		}
		//res denotes the amount of successful read
		//start denotes the position we should fetch data from file
		byte[] buff;
		int res = 0;
		while(length > 0) {
			int can_read = Math.min(length, pageSize);
			buff = new byte[can_read];
			if (instan.read(buff, 0, can_read) != can_read) {
				return -1;
			}
			int write_back = this.writeVirtualMemory(vaddr, buff, 0, can_read);
			if (write_back != can_read) {
				return -1;
			}
			res = res + can_read;
			length = length - can_read;
			vaddr = vaddr + can_read;	
		}
		return res;
	}
	
	private int handleWrite(int fileDescriptor, int vaddr, int length) {
		if (fileDescriptor <= 0 || fileDescriptor >= 16) {
			return -1;
		}
		if (length < 0 || vaddr < 0) {
			return -1;
		}
		OpenFile instan = this.file_arr[fileDescriptor];
		if (instan == null) {
			return -1;
		}
		int res = 0;
		byte[] temp_store;
		while (length > 0) {
			int can_write = Math.min(pageSize, length);
			temp_store = new byte[can_write];
			if(this.readVirtualMemory(vaddr, temp_store, 0, can_write) != can_write) {
				return -1;
			}
			int write_back = instan.write(temp_store, 0, can_write);
			if (write_back == -1 || write_back != can_write) {
				return -1;
			}
			res = res + can_write;
			length = length - can_write;
			vaddr = vaddr + can_write;
		}
		return res;
	}

	
	private int handleClose(int fileDescriptor) {
		if (fileDescriptor < 0 || fileDescriptor >= 16) {
			return -1;
		}
		OpenFile instan = this.file_arr[fileDescriptor];
		if (instan == null) {
			return -1;
		}
		instan.close();
		this.file_arr[fileDescriptor] = null;
		return 0;
	}
	
	
	private int handleUnlink(int vaddr) {
		String get_filename = new UserProcess().readVirtualMemoryString(vaddr, 256);
		if (get_filename == null) {
			return -1;
		}
		if (vaddr < 0) {
			return -1;
		}
		for (int i = 0; i < this.file_arr.length; i ++) {
			if (this.file_arr[i] != null) {
				if (this.file_arr[i].getName().equals(get_filename)) {
					this.handleClose(i);
				}
			}
		}
		if(ThreadedKernel.fileSystem.remove(get_filename)) {
			return 0;
		}else {
			return -1;
		}
	}
	
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
    protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
	
	private OpenFile[] file_arr;
}
