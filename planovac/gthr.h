

#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include <sys/time.h>

struct timeval t_program_start, t_program_stop;

enum {
    MaxGThreads = 5,		// Maximum number of threads, used as array size for gttbl
    StackSize = 0x400000,	// Size of stack of each thread
};

struct gt {
  int priority; //0 - 10 (0 max, 10 min)
  
  struct timeval t_start, t_stop, t_running;
  long min;
  long max;
  long avg;
  long switch_count;
  // Saved context, switched by gtswtch.S (see for detail)
  struct gtctx {
    uint64_t rsp;
    uint64_t r15;
    uint64_t r14;
    uint64_t r13;
    uint64_t r12;
    uint64_t rbx;
    uint64_t rbp;
  }
  ctx;
  // Thread state
  enum {
    Unused,
    Running,
    Ready,
  }
  st;
};

struct gt gttbl[MaxGThreads];	// statically allocated table for thread control
struct gt * gtcur;				// pointer to current thread

void gtinit(void);				// initialize gttbl
void gtret(int ret);			// terminate thread
void gtswtch(struct gtctx * old, struct gtctx * new);	// declaration from gtswtch.S
bool gtyield(void);				// yield and switch to another thread
void gtstop(void);				// terminate current thread
int gtgo(void( * f)(void));		// create new thread and set f as new "run" function
void resetsig(int sig);			// reset signal
void gthandle(int sig);			// periodically triggered by alarm
int uninterruptibleNanoSleep(time_t sec, long nanosec);	// uninterruptible sleep