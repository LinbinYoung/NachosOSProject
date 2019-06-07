# Project 3: Demand Paging

## Group member: 

Chaohui Yu, Linbin Yang, Chih-hung cheng
## Short description

### part1:  Implement demand paging. 
-- In VMProcess.loadSections, initialize all of the TranslationEntries as invalid. 
-- Handle page fault exceptions in VMProcess.handleException. 
-- Add a method to prepare the requested page on demand. 
-- Update readVirtualMemory and writeVirtualMemory to handle invalid pages and page faults. 

### part2 Now implement demand paged virtual memory with page replacement.
-- Extend page fault exception handler to evict pages once physical memory becomes full. 
-- Evict the victim page. If the page is clean (i.e., not dirty), then the page can be used immediately.
-- Read in the contents of the faulted page either from the executable file or from swap.
-- Implement the swap file for storing pages evicted from physical memory.

## Test
we tested all the basic test cases for proj3 including swap4 and swap5. In order to check the implementation of Clock Algorithm, we set
the Physical Page Size to 4, 8, 16 and 32.
We also test some test cases from proj2 including halt, write1, write4 and write10.

## Group Contribution
- Chaohui Yu: design & write the code, testing, debugging
- Linbin Yang: design & write the code, readme, debugging
- Chih-hung cheng: design & write the code, debugging