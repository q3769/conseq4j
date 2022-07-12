package conseq4j.service;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface ConcurrentSequencerService {

    void execute(Runnable command, Object sequenceKey);

    <T> Future<T> submit(Callable<T> task, Object sequenceKey);
}
