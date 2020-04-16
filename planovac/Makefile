CFLAGS = -g -Wall

gttest: gthr.o gtswtch.o main.o
	$(CC) -o $@ $^

.S.o:
	as -o $@ $^

.PHONY: clean
clean:
	rm -f *.o gttest
