package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Hashtable;

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
//		int numPhysPages = Machine.processor().getNumPhysPages();
//		pageTable = new TranslationEntry[numPhysPages];
//		for (int i = 0; i < numPhysPages; i++)
//			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		UserKernel.procLock.acquire();
		this.pid = UserKernel.procIDCounter++;
		UserKernel.numOfRunningProcess++;
		UserKernel.procLock.release();
		
		openFileTable = new OpenFile[16];
		openFileTable[0] = UserKernel.console.openForReading();
		openFileTable[1] = UserKernel.console.openForWriting();
		
		
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
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		if (data == null || vaddr < 0) return 0;
		
		byte[] memory = Machine.processor().getMemory();
		
		int sucessTransfer = 0;
		while (length > 0) {
			
			if (vaddr >= numPages*pageSize)
				return sucessTransfer;
			
			int vpn = Processor.pageFromAddress(vaddr);
			int p_offset = Processor.offsetFromAddress(vaddr);
			int readLen = Math.min(pageSize - p_offset, length);
			
			if (this.pageTable[vpn].valid == false)
				return sucessTransfer;
			
			int paddr = this.pageTable[vpn].ppn * pageSize + p_offset;
			
			try {
				System.arraycopy(memory, paddr, data, offset, readLen);
			}catch(Exception e){
				return sucessTransfer;
			}
		
			this.pageTable[vpn].used = true;
			vaddr += readLen;
			sucessTransfer += readLen;
			length -= readLen;
			offset += readLen;
		}
		return sucessTransfer;
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
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		if (vaddr < 0) return 0;
		byte[] memory = Machine.processor().getMemory();
		
		int sucessWrite = 0; 
		
		while (length > 0) {
			
			int vpn = Processor.pageFromAddress(vaddr);
			int p_offset = Processor.offsetFromAddress(vaddr);
			
			if (!this.pageTable[vpn].valid || this.pageTable[vpn].readOnly)
				return sucessWrite;
			
			int writeLen = Math.min(length, pageSize-p_offset);
			int paddr = this.pageTable[vpn].ppn * pageSize + p_offset;
			
			try {
				System.arraycopy(data, offset, memory, paddr, writeLen);
			}catch(Exception e){
				return sucessWrite;
			}
		
			this.pageTable[vpn].used = true;
			this.pageTable[vpn].dirty = true;
			
			length -= writeLen;
			vaddr += writeLen;
			offset += writeLen;
			sucessWrite += writeLen;
			
		}

		return sucessWrite;
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
		
		int numPhysPages = Machine.processor().getNumPhysPages();
		
		if (this.numPages > numPhysPages) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		UserKernel.phyPagelLock.acquire();
		this.pageTable = new TranslationEntry[this.numPages];
		int ppn;
		
		for (int i = 0; i < numPages; ++i) {
			try {
				ppn = UserKernel.freePhyPages.remove(0);
			}catch (IndexOutOfBoundsException e) {
				this.unloadSections();
				return false;
			}
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		}
		UserKernel.phyPagelLock.release();
		
		
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				
				pageTable[vpn].readOnly = section.isReadOnly();
				pageTable[vpn].valid = true;
				section.loadPage(i, pageTable[vpn].ppn);
				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		
		for (int i = 0; i < this.numPages; ++i) {
			if (this.pageTable[i].valid == true) {
				
				this.pageTable[i].valid = false;
				UserKernel.phyPagelLock.acquire();
				Integer e = new Integer(this.pageTable[i].ppn);
				UserKernel.freePhyPages.add(e);
				UserKernel.phyPagelLock.release();
			}
		}
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
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		// Kernel.kernel.terminate();
		

		// Close all files in file table
		for (int i = 2; i < 16; ++i) {
			if (this.openFileTable[i] != null) {
				this.openFileTable[i].close();
				this.openFileTable[i] = null;
			}
		}
		// Delete all memory by calling UnloadSections()
		this.unloadSections();
		
		// Close the coff by calling coff.close()
		this.coff.close();
		
		// If it has a parent process, save the status for parent
		// Wake up parent if sleeping
		System.out.println("========"+UserKernel.numOfRunningProcess);
		
		UserKernel.procLock.acquire();
		--UserKernel.numOfRunningProcess;
		UserKernel.procLock.release();
		
		if (this.parentProcess != null) {
			System.out.println("=======The parent Process is not null");
			this.exitStatus = status;
			this.normalExit = true;
			this.thread.finish();
			
		}
		// In case of last process, call kernel.kernel.terminate()
		// Close KThread by calling KThread.finish()
		if (UserKernel.numOfRunningProcess == 0) {
			System.out.println("=======This is the last process");
			this.exitStatus = status;
			this.normalExit = true;
			Kernel.kernel.terminate();
		}
		//KThread.finish();

		return 0;
	}
	
	
	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int viAddr) {
		
		String fileName = readVirtualMemoryString(viAddr, 256);
		
		if (fileName == null) return -1;
		
		
		for (int i = 0; i < openFileTable.length; ++i){
			if (openFileTable[i] == null) {
				OpenFile o_instance = ThreadedKernel.fileSystem.open(fileName, false);
				
				// then the user process is not allowed to access the given file
				if (o_instance == null) return -1;
				
				openFileTable[i] = o_instance;
				return i;

			}
		}
		
		return -1;
	}
	
	/**
	 * Handle the create() system call.
	 */
	
	private int handleCreate(int viAddr) {
		
		String fileName = readVirtualMemoryString(viAddr, 256);
		
		if (fileName == null) return -1;
		
		for (int i = 0; i < openFileTable.length; ++i){
			if (openFileTable[i] == null) {
				OpenFile o_instance = ThreadedKernel.fileSystem.open(fileName, true);
				
				// then the user process is not allowed to access the given file
				if (o_instance == null) return -1;
				
				openFileTable[i] = o_instance;
				return i;
			}
		}
		
		return -1;
		
	}
	
	/**
	 * Handle the read() system call.
	 */
	
	private int handleRead(int fileDescriptor, int viAddr, int count) {
		
		
		// fileDescriptor is invalid
		if (fileDescriptor < 0 || fileDescriptor > 15 || fileDescriptor == 1)
			return -1;
		if (openFileTable[fileDescriptor] == null)
			return -1;
		if (count < 0)
			return -1;
		
		OpenFile of_instance = openFileTable[fileDescriptor];
		int successRead = 0;
		
		while (count > 0) {

			int readLen = Math.min(count, pageSize);
			byte[] tmp_data = new byte[readLen];
			
			int readByte =  of_instance.read(tmp_data,0 ,readLen);			
			if (readByte == -1 || readByte != readLen)
				return -1;
			
			
			int byteTransfer =  this.writeVirtualMemory(viAddr, tmp_data);
			if (byteTransfer != readByte)
				return -1;
			
			// move the pointer in virtual address
			viAddr += byteTransfer;
			count -= byteTransfer;
			successRead += byteTransfer;
		}
		
		return successRead;
		
	}
	
	private int handleWrite(int fileDescriptor, int viAddr, int count) {
		
		if (fileDescriptor < 0 || fileDescriptor > 15 || fileDescriptor == 0) return -1;
		if (openFileTable[fileDescriptor] == null) return -1;
		if (count <0) return -1;
		
		OpenFile of_instance = openFileTable[fileDescriptor];
		int sucessWrite = 0;
		
		
		while (count > 0) {
			int readLen = Math.min(pageSize, count);
			byte[] tmp_data = new byte[readLen];
			
			int readByte = this.readVirtualMemory(viAddr, tmp_data, 0, readLen);
			if (readByte != readLen)
				return -1;
			
			int writeByte = of_instance.write(tmp_data, 0, readByte);
			if (writeByte  == -1 || writeByte != readByte)
				return -1;
			
			// move the pointer in virtual address
			viAddr += writeByte;
			count -= writeByte;
			sucessWrite += writeByte;
		}
		return sucessWrite;
		
	}

	private int handleClose(int fileDescriptor) {
		if (fileDescriptor < 0 || fileDescriptor > 15) return -1;
		if (openFileTable[fileDescriptor] == null) return -1;
		
		openFileTable[fileDescriptor].close();
		openFileTable[fileDescriptor] = null;
		return 0;
	}
	
	private int handleUnlink(int viAddr) {
		
		String fileName = this.readVirtualMemoryString(viAddr, 256);
		if (fileName == null) return -1;
		
		// If this file in the table
		for (int i =0; i < 16; ++i) {
			if (openFileTable[i] != null && openFileTable[i].getName() == fileName) {
				openFileTable[i].close();
				openFileTable[i] = null;
			}
		}
		
		if (ThreadedKernel.fileSystem.remove(fileName) == true)
			return 0;
		else return -1;
	}
	
	private int handleExec(int viAddrFileName, int argc, int viAddrArg) {
		
		String fileName = this.readVirtualMemoryString(viAddrFileName, 256);
		if (fileName == null || !fileName.endsWith(".coff"))
			return -1;
		if (argc < 0 ) 
			return -1;
		// byteAddr stores the pointer 
		/* argv is an array of pointers to null-terminated strings 
		 * that represent the arguments to pass to the child process.
		 */
		byte[] byteAddr = new byte[4*argc];
		int byteRead =  this.readVirtualMemory(viAddrArg, byteAddr);	
		if (byteRead != byteAddr.length)
			return -1;
		
		// Get all the argument in string type
		String[] args = new String[argc];
		for (int i = 0; i < argc; ++i) {
			
			int argViAddr = Lib.bytesToInt(byteAddr, i*4);
			args[i] = this.readVirtualMemoryString(argViAddr, 256);
			if (args[i] == null) return -1;
		}
		
		UserProcess childProcess = UserProcess.newUserProcess();
		childProcess.parentProcess = this;
		this.childMap.put(new Integer(childProcess.pid), childProcess);
		
		//UserKernel.procLock.acquire();
		boolean sucess =  childProcess.execute(fileName, args);
		if (sucess == false) {
			UserKernel.procLock.release();
			return -1;
		}
		//UserKernel.procLock.release();
		return childProcess.pid;
	}
	
	private int handleJoin(int childPID, int viAddr) {
		
		
		Integer cPid = new Integer(childPID);
		if (!this.childMap.containsKey(cPid))
			return -1;
		// Sleep on child
	    // Get and set child status
		UserProcess child =  this.childMap.get(cPid);
		child.thread.join();
		// can't join again
		childMap.remove(cPid);
		
		
		if (child.normalExit) {
			int childExitStatus = child.exitStatus.intValue();
			int byteWrite = this.writeVirtualMemory(viAddr, Lib.bytesFromInt(childExitStatus));
			if (byteWrite < 4) return -1;
			return 1;
		}
	
		return 0;
		
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
		case syscallCreate:
			return handleCreate(a0);
			
		case syscallOpen:
			return handleOpen(a0);
			
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		
		

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
			
			System.out.println("======This is default handle exception");
			this.handleExit(999);
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
	
	
	//  file descriptor table
	//  a file table size of 16, supporting up to 16 concurrently open files per process
	private OpenFile[] openFileTable;
	
	
	// the parent process
	private UserProcess parentProcess;
	
	// pid
	private int pid;
	
	// Condition to wake up the parent
	private Integer exitStatus;
	private boolean normalExit;
	
	
	// keep the reference of the child
	private Hashtable<Integer, UserProcess> childMap = new Hashtable<Integer,UserProcess> ();
	
}
