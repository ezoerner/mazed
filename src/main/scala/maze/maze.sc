import maze.MazeBuilder

val grid = MazeBuilder.build(20,20)

println(grid.printGrid().mkString("\n"))

