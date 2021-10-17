# qlib-conseq (Concurrent Sequencer)

As an API client of this JAVA lib, I want to summon a thread/executor by a sequence key, so that all related tasks with the same sequence key are executed sequentially by the same executor while unrelated tasks with different sequence keys can be executed as concurrently as can be by different executors.
