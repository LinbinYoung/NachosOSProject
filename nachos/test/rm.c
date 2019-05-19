#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{

    argc = 2;
    argv[1] =  "test_rm.txt";

    creat(argv[1]);

  if (argc!=2) {
    printf("Usage: rm <file>\n");
    return 1;
  }


  if (unlink(argv[1]) != 0) {
      printf("Unable to remove %s\n", argv[1]);
      return 1;
  }

  char*  s = "remove sucessful!";
  printf("%s\n",s);
  return 0;
}
