#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{

   int argc = 2;
   char *argv[1] =  {"", "test_rm.txt"}


  if (argc!=2) {
    printf("Usage: rm <file>\n");
    return 1;
  }


  if (unlink(argv[1]) != 0) {
      printf("Unable to remove %s\n", argv[1]);
      return 1;
  }
  
  printf("%s\n","remove sucessful!" );
  return 0;
}
