# Project 2: Multiprogramming ReadME

## Group member: 

Chaohui Yu, Linbin Yang, Chih-hung cheng
## Short description

### part1: Implement the file system calls
- create: First of all, read the file name from virtual address, and then create the file by fileSystem call. Then, to see if it's already exits in the open file table and check if the table has space for this file. It will return the file descriptor. 

- open: First of all, read the file name from virtual address, and then open the file by fileSystem call. Then to see if it can be put into the open file table, and return the file descriptor.

- read: read the data from the file identified by file descriptor, and then write this data back to the buffer identified by virtual address.

- write: read the data from the virtual address space, and then write the data back to the file identified by file descriptor.

- close: close the file by fileSystem call and reset the open file table.

- unlink: read the file name from virtual addres, and then close the file and remove the file by fileSystem call.

### part2 Implement support for multiprogramming.
- modefy loadSections: create the page table by the num of pages and then get free physical page from kernel. Map the page table entry to the phyiscal page.

- Modify UserProcess.readVirtualMemory & UserProcess.writeVirtualMemory:
	transform the virtual address to physical address, and copy data between the kernel and the user's virtual address space

### part3  Implement the system calls exec, join, and exit

- exec: 
	1. read file name from virtual address
	2. read all the argv from the virtual address of the argv pointer
	3. create a new process to execute and store the info of child & parent
- join:
	1. join on the child
	2. Get and set child status
	3. wirte the status to virtual address

- exit:
	1. Close all files in file table
	2. Delete all memory by calling UnloadSections()
	3. Close the coff by calling coff.close()
	4. If it has a parent process, save the status for parent
	5. In case of last process, call kernel.kernel.terminate()

## Test the call
we tested all the basic test cases. And also test the join to different kinds of program.
Also, test the cat, cp, echo, halt, mv, rm, sh, short.
## Group Contribution

- Chaohui Yu: design & write the code, testing, readme
- Linbin Yang: design & write the code, debugging
- Chih-hung cheng: design & write the code, implementation for the pipe.
