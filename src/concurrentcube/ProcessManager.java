package concurrentcube;

import concurrentcube.Rotations.Rotation;

import java.util.concurrent.Semaphore;

public class ProcessManager {
    /* Mutual exclusion semaphore implemented as a binary semaphore. */
    private final Semaphore varProtection;
    /* Semaphore to hang awaiting `show` requests */
    private final Semaphore readerSem;
    /* Semaphores to guarantee mutual exclusion between rotations of the plane */
    private final Semaphore[] planeMutexes;
    /* Semaphores to hang rotations conflicting with the currently working axis */
    private final Semaphore[] axisSems;

    /* Number of processes that currently displaying the cube */
    private int activeReaders;
    /* Number of processes waiting to call `show` */
    private int waitingReaders;
    /* Number of processes waiting to call `rotate` */
    private int waitingWriters;
    /* Number of processes currently rotating the cube */
    private int activeWriters;

    /* Number of writers from each axis waiting for cube access */
    private final int[] waitingFromAxis;

    /* Contains the side ID of the first process that initiated any rotations in its group */
    private WorkingGroup currentAxis;
    /* ID of the last group of writers that finished their work */
    private WorkingGroup lastFinishedGroup;

    /* Cube handled by the manager */
    private final Cube cube;

    public ProcessManager(Cube cube) {
        this.cube = cube;

        this.varProtection = new Semaphore(1);
        this.readerSem = new Semaphore(0, true);

        this.waitingFromAxis = new int[WorkingGroup.NUM_AXES.intValue()];
        this.axisSems = new Semaphore[WorkingGroup.NUM_AXES.intValue()];
        for (int axis = 0; axis < WorkingGroup.NUM_AXES.intValue(); axis++) {
            this.axisSems[axis] = new Semaphore(0);
        }

        this.planeMutexes = new Semaphore[cube.getSize()];
        for (int plane = 0; plane < cube.getSize(); plane++) {
            this.planeMutexes[plane] = new Semaphore(1);
        }

        this.lastFinishedGroup = WorkingGroup.Readers;
    }

    private int findNextWaitingWriterGroup(int axis) {
        for (int i = 1; i <= WorkingGroup.NUM_AXES.intValue(); i++) {
            int j = (axis + i) % WorkingGroup.NUM_AXES.intValue();
            if (waitingFromAxis[j] > 0) {
                return j;
            }
        }
        return -1;
    }

    /**
     * Enables a process to enter its entry protocol, waits on the `varMutex` if necessary.
     */
    public void entryProtocol() throws InterruptedException {
        varProtection.acquire();
    }

    /**
     * A writer must wait if readers or other colliding writers
     * are currently handling the cube.
     * @param writer : data of the requested writer
     * @return should the writer wait
     */
    private boolean writerWaitCondition(Rotation writer) {
        return activeReaders > 0 || (activeWriters > 0 && (currentAxis != writer.getAxis()));
    }

    private boolean readerWaitCondition() {
        return activeWriters > 0 || waitingWriters > 0;
    }

    /**
     * Halts a writer-type process before entering the critical section
     * if there are other active processes inside that would collide with it
     * (i.e. readers or non-parallel writers)
     * @param writer : data of the requested writer
     */
    public void writerWaitIfNecessary(Rotation writer) throws InterruptedException {
        try {
            if (writerWaitCondition(writer)) {
                waitingWriters++;
                waitingFromAxis[writer.getAxis().intValue()]++;
                varProtection.release();

                axisSems[writer.getAxis().intValue()].acquire();

                waitingWriters--;
                waitingFromAxis[writer.getAxis().intValue()]--;
            }
        } catch (InterruptedException e) {
            // the process was interrupted after being woken by an exiting process
            if (axisSems[writer.getAxis().intValue()].tryAcquire()) {
                waitingWriters--;
                waitingFromAxis[writer.getAxis().intValue()]--;

                // resume waking starting from my group
                if (waitingFromAxis[writer.getAxis().intValue()] > 0) {
                    axisSems[writer.getAxis().intValue()].release();
                } else if (lastFinishedGroup == WorkingGroup.Readers) {
                    if (waitingReaders > 0) {
                        readerSem.release();
                    } else {
                        varProtection.release();
                    }
                } else {
                    varProtection.release();
                }
                axisSems[writer.getAxis().intValue()].release();
            } else {
                // the process was interrupted before trying to acquire its semaphore
                varProtection.acquireUninterruptibly();
                waitingWriters--;
                waitingFromAxis[writer.getAxis().intValue()]--;
                varProtection.release();
            }
            throw e;
        }
    }

    /**
     * Halts a reader before entering the critical section
     * if there are other active processes inside that would collide with it.
     * (i.e. any writers)
     */
    public void readerWaitIfNecessary() throws InterruptedException {
        try {
            if (readerWaitCondition()) {
                waitingReaders++;
                varProtection.release();

                readerSem.acquire();

                waitingReaders--;
            }
        } catch(InterruptedException e) {
            // the process was interrupted after being woken by an exiting process
            if (readerSem.tryAcquire()) {
                waitingReaders--;
                // resume waking starting from my group
                if (waitingReaders > 0) {
                    readerSem.release();
                } else if (lastFinishedGroup != WorkingGroup.Readers) {
                    if (waitingWriters > 0) {
                        int nextGroup = findNextWaitingWriterGroup(lastFinishedGroup.intValue());
                        if (nextGroup != -1) {
                            axisSems[nextGroup].release();
                        } else {
                            varProtection.release();
                        }
                    }
                } else {
                    varProtection.release();
                }
                readerSem.release();
            } else {
                // the process was interrupted before trying to acquire its semaphore
                varProtection.acquireUninterruptibly();
                waitingWriters--;
                varProtection.release();
            }
            throw e;
        }
    }

    /**
     * Sets a layer's status to occupied,
     * indicating that a rotation on it has begun.
     * @param writer : data of the working writer
     */
    public void occupyPlane(Rotation writer) throws InterruptedException {
        activeWriters++;
        if (activeWriters == 1) {
            currentAxis = writer.getAxis();
        }

        planeMutexes[writer.getPlane()].acquire();
    }

    /**
     * Wakes up other writers performing rotations on free layers around the same axis.
     * @param writer : data of the current writer
     */
    public void inviteParallelWriters(Rotation writer) {
        if (waitingFromAxis[writer.getAxis().intValue()] > 0) {
            axisSems[writer.getAxis().intValue()].release();
        } else {
            varProtection.release();
        }
    }

    /**
     * Wakes up other readers to read data from the cube concurrently.
     */
    public void inviteParallelReaders() {
        activeReaders++;

        if (waitingReaders > 0) {
            readerSem.release();
        } else {
            varProtection.release();
        }
    }

    /**
     * Allows a writer to write to the cube.
     * @param writer : what is being written to the cube
     */
    public void writeToCube(Rotation writer) {
        if (cube.getBeforeRotation() != null) {
            cube.getBeforeRotation().accept(writer.getSide().intValue(), writer.getLayer());
        }
        writer.applyRotation();
        if (cube.getAfterRotation() != null) {
            cube.getAfterRotation().accept(writer.getSide().intValue(), writer.getLayer());
        }
    }

    /**
     * Allows a reader to read from the cube.
     */
    public String readFromCube() {
        if (cube.getBeforeShowing() != null) {
            cube.getBeforeShowing().run();
        }
        String str = cube.toString();
        if (cube.getAfterShowing() != null) {
            cube.getAfterShowing().run();
        }
        return str;
    }

    /**
     * Indicates that a writer is abandoning his critical section,
     * and allows other writers and readers to enter, prioritizing readers.
     * @param writer - data of the abandoning process
     */
    public void writerExitProtocol(Rotation writer) {
        planeMutexes[writer.getPlane()].release();

        varProtection.acquireUninterruptibly();
        activeWriters--;

        if (activeWriters == 0) {
            lastFinishedGroup = writer.getAxis();
            if (waitingReaders > 0) {
                readerSem.release();
            } else {
                int nextGroup = findNextWaitingWriterGroup(writer.getAxis().intValue());
                if (nextGroup != -1) {
                    axisSems[nextGroup].release();
                } else {
                    varProtection.release();
                }
            }
        } else {
            varProtection.release();
        }
    }

    /**
     * Indicates that a reader is abandoning his critical section,
     * and allows other writers and readers to enter, prioritizing writers.
     */
    public void readerExitProtocol() {
        varProtection.acquireUninterruptibly();
        activeReaders--;
        lastFinishedGroup = WorkingGroup.Readers;

        if (activeReaders == 0) {
            if (waitingWriters > 0) {
                int nextGroup = findNextWaitingWriterGroup(lastFinishedGroup.intValue());
                if (nextGroup != -1) {
                    axisSems[nextGroup].release();
                } else {
                    varProtection.release();
                }
            } else if (waitingReaders > 0) {
                readerSem.release();
            } else {
                varProtection.release();
            }
        } else {
            varProtection.release();
        }
    }

}
