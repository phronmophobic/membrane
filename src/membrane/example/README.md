Each of the examples can be run from the command line if membrane is a dependency.

If membrane is checked out locally, then running with `clj` requires prepping first:

```sh
clojure -T:build compile
```

## Todo
`lein run -m membrane.example.todo`  
`clojure -M:skialib -m membrane.example.todo`  
![todo](/docs/images/todo.gif?raw=true)

## Kitchen Sink
`lein run -m membrane.example.kitchen-sink`  
`clojure -M:skialib -m membrane.example.kitchen-sink`  

## Counter
`lein run -m membrane.example.counter`  
`clojure -M:skialib -m membrane.example.counter`  
![simple counter](/docs/images/counter3.gif?raw=true)

## File Selector

`lein run -m membrane.example.file-selector /path/to/folder/`  
`clojure -M:skialib -m membrane.example.file-selector /path/to/folder/`  
![item selector](/docs/images/item-selector.gif?raw=true)

## Terminal Todo
`lein run -m membrane.example.terminal-todo`  
`clojure -M:lanterna -m membrane.example.terminal-todo`  






