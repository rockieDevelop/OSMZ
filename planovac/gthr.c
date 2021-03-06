#define _DEFAULT_SOURCE
#include "gthr.h"

// function triggered periodically by timer (SIGALRM)
void gthandle(int sig) {
  if(sig == SIGALRM){
    gtyield();
  }
    
  else if(sig == SIGINT){
    //print statistics table
    gettimeofday(&t_program_stop,NULL);
    struct timeval t_program_running;
    timersub(&t_program_stop, &t_program_start, &t_program_running); //program running time
    
    printf("\n");
    struct gt * p;
    for (p = & gttbl[0];; p++){
      if (p == & gttbl[MaxGThreads])
        break;
      
      struct timeval t_wait;
      timersub(&t_program_running, &p->t_running, &t_wait); //calculate thread's waiting time
      p->avg = (p->t_running.tv_sec * 1000000 + p->t_running.tv_usec) / p->switch_count; //calculate thread's average running time
      printf("running: %ld.%06ld | waiting: %ld.%06ld | min: 0.%06ld | max: 0.%06ld | avg: 0.%06ld\n", p->t_running.tv_sec, p->t_running.tv_usec, t_wait.tv_sec, t_wait.tv_usec, p->min, p->max, p->avg);
    }
    exit(0);
  }
}


// initialize first thread as current context
void gtinit(void) {
  gettimeofday(&t_program_start,NULL); //program start time

  gtcur = & gttbl[0];			// initialize current thread with thread #0
  gtcur -> st = Running;		// set current to running
  signal(SIGALRM, gthandle);	// register SIGALRM, signal from timer generated by alarm
  
  signal(SIGINT, gthandle); //register SIGINT, Ctrl+c from keyboard
}

// exit thread
void __attribute__((noreturn)) gtret(int ret) {
  if (gtcur != & gttbl[0]) {	// if not an initial thread,
    gtcur -> st = Unused;		// set current thread as unused
    gtyield();					// yield and make possible to switch to another thread
    assert(!"reachable");		// this code should never be reachable ... (if yes, returning function on stack was corrupted)
  }
  while (gtyield());			// if initial thread, wait for other to terminate
  exit(ret);
}

// switch from one thread to other
bool gtyield(void) {
  struct gt * p;
  struct gtctx * old, * new;

  resetsig(SIGALRM);			// reset signal

  p = gtcur;
  if(p->st == Running){
    gettimeofday(&gtcur->t_stop,NULL); //stop counting running time
    struct timeval sub_result;
    timersub(&gtcur->t_stop, &gtcur->t_start, &sub_result); //calculate running time of the thread
    
    if(gtcur->t_start.tv_sec != 0){
      gtcur->switch_count += 1;
      timeradd(&gtcur->t_running, &sub_result, &gtcur->t_running); //add calculated time to total running time
      if(sub_result.tv_usec > gtcur->max)
        gtcur->max = sub_result.tv_usec;
      if(sub_result.tv_usec < gtcur->min)
        gtcur->min = sub_result.tv_usec;
    }
  }
  
  while (p -> st != Ready) {			// iterate through gttbl[] until we find new thread in state Ready 
    if (++p == & gttbl[MaxGThreads])	// at the end rotate to the beginning
      p = & gttbl[0];
    if (p == gtcur)						// did not find any other Ready threads
      return false;
  }

  if (gtcur -> st != Unused)		// switch current to Ready and new thread found in previous loop to Running
    gtcur -> st = Ready;
  p -> st = Running;
  old = & gtcur -> ctx;					// prepare pointers to context of current (will become old) 
  new = & p -> ctx;						// and new to new thread found in previous loop
  gtcur = p;							// switch current indicator to new thread
  gettimeofday(&gtcur->t_start,NULL); //start counting running time
  gtswtch(old, new);					// perform context switch (assembly in gtswtch.S)
  return true;
}

// return function for terminating thread
void gtstop(void) {
  gtret(0);
}

// create new thread by providing pointer to function that will act like "run" method
int gtgo(void( * f)(void)) {
  char * stack;
  struct gt * p;

  for (p = & gttbl[0];; p++)			// find an empty slot
    if (p == & gttbl[MaxGThreads])		// if we have reached the end, gttbl is full and we cannot create a new thread
      return -1;
    else if (p -> st == Unused)
    break;								// new slot was found

  //initialize values
  p->t_running = (struct timeval){0}; //initialize running time to 0
  p->min = 999999;
  p->max = 0;
  p->avg = 0;
  p->switch_count = 0;

  stack = malloc(StackSize);			// allocate memory for stack of newly created thread
  if (!stack)
    return -1;

  *(uint64_t * ) & stack[StackSize - 8] = (uint64_t) gtstop;	//	put into the stack returning function gtstop in case function calls return
  *(uint64_t * ) & stack[StackSize - 16] = (uint64_t) f;		//  put provided function as a main "run" function
  p -> ctx.rsp = (uint64_t) & stack[StackSize - 16];			//  set stack pointer
  p -> st = Ready;												//  set state

  return 0;
}

void resetsig(int sig) {
  if (sig == SIGALRM) {
    alarm(0);			// Clear pending alarms if any
  }

  sigset_t set;				// Create signal set
  sigemptyset( & set);		// Clear it
  sigaddset( & set, sig);	// Set signal (we use SIGALRM)

  sigprocmask(SIG_UNBLOCK, & set, NULL);	// Fetch and change the signal mask

  if (sig == SIGALRM) {
    // Generate alarms
    ualarm(500, 500);		// Schedule signal after given number of microseconds
  }
}

int uninterruptibleNanoSleep(time_t sec, long nanosec) {
  struct timespec req;
  req.tv_sec = sec;
  req.tv_nsec = nanosec;

  do {
    if (0 != nanosleep( & req, & req)) {
      if (errno != EINTR)
        return -1;
    } else {
      break;
    }
  } while (req.tv_sec > 0 || req.tv_nsec > 0);
  return 0; /* Return success */
}
