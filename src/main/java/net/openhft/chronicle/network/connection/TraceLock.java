package net.openhft.chronicle.network.connection;

import net.openhft.chronicle.core.Jvm;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Rob Austin.
 */
public class TraceLock extends ReentrantLock {

    public static ReentrantLock create() {
        return Jvm.isDebug() ? new TraceLock() : new ReentrantLock();
    }

    private volatile Throwable here;

    @Override
    public void lockInterruptibly() throws InterruptedException {
        super.lockInterruptibly();
        here = new Throwable();
    }

    @Override
    public void lock() {


        super.lock();
        here = new Throwable();
    }

    @Override
    public String toString() {

        if (here == null)
            return super.toString();

        final StringBuilder sb = new StringBuilder(super.toString());

        for (StackTraceElement s : here.getStackTrace()) {
            sb.append("\n\tat ").append(s);
        }


        return sb.toString();

    }

    @Override
    public void unlock() {
        if (getHoldCount() == 1)
            here = null;
        super.unlock();
    }


    @Override
    public boolean tryLock() {
        final boolean b = super.tryLock();
        if (b)
            here = new Throwable();
        return b;
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {

        final boolean b = super.tryLock(timeout, unit);
        if (b)
            here = new Throwable();
        return b;
    }
}
