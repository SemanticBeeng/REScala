#!/usr/bin/perl
use 5.020;

use strict;
use warnings;
use utf8;
use experimental 'signatures';
no if $] >= 5.018, warnings => "experimental::smartmatch";

use DBI;
use Text::CSV_XS qw( csv );
use Data::Dumper;
use Chart::Gnuplot;
use File::Find;
use File::Path qw(make_path remove_tree);

# combining standard deviations is not trivial, but would be possible:
# http://www.burtonsys.com/climate/composite_standard_deviations.html

{
  my $dbPath = ':memory:';
  my $table = 'results';
  my $csvDir = 'resultStore';
  my $outDir = 'fig';

  my $dbh = DBI->connect("dbi:SQLite:dbname=". $dbPath,"","",{AutoCommit => 0,PrintError => 1});

  my @engines = ("synchron", "parrp", "stm", "fair");

  importCSV($csvDir, $dbh, $table);

  remove_tree($outDir);
  mkdir $outDir;
  chdir $outDir;

  for my $dynamic (qw<static dynamic>) {
    for my $philosophers (queryChoices($dbh, $table, "Param: philosophers", "Param: tableType" => $dynamic)) {
      for my $layout (queryChoices($dbh, $table, "Param: layout", "Param: tableType" => $dynamic, "Param: philosophers" => $philosophers)) {
        plotBenchmarksFor($dbh, $table, "${dynamic}philosophers$philosophers", $layout,
          map { {Title => $_, "Param: engineName" => $_ , Benchmark => "benchmarks.philosophers.PhilosopherCompetition.eat",
          "Param: philosophers" => $philosophers, "Param: layout" => $layout, "Param: tableType" => $dynamic } } queryChoices($dbh, $table, "Param: engineName", "Param: tableType" => $dynamic, "Param: philosophers" => $philosophers, "Param: layout" => $layout));
      }
    }

    my $byPhilosopher = sub($engine) {
      my @choices =  queryChoices($dbh, $table, "Param: philosophers", "Param: engineName" => $engine, "Param: layout" => "alternating", "Param: tableType" => $dynamic );
      map { {Title => $engine . " " . $_, "Param: engineName" => $engine , Benchmark => "benchmarks.philosophers.PhilosopherCompetition.eat",
        "Param: philosophers" => $_, "Param: layout" => "alternating", "Param: tableType" => $dynamic } } (
         @choices);
    };
    my @list = map { $byPhilosopher->($_) } (queryChoices($dbh, $table, "Param: engineName", "Param: tableType" => $dynamic));
    plotBenchmarksFor($dbh, $table, "${dynamic}philosophers", "philosopher comparison engine scaling", @list);


    plotBenchmarksFor($dbh, $table, "${dynamic}philosophers", "Philosopher Table",
      map { {Title => $_, "Param: engineName" => $_ , Benchmark =>  "benchmarks.philosophers.PhilosopherCompetition.eat", "Param: tableType" => $dynamic } }  queryChoices($dbh, $table, "Param: engineName", "Param: tableType" => $dynamic));
  }


  plotBenchmarksFor($dbh, $table, "stacks", "Dynamic",
    map {{Title => $_, "Param: work" => 0, "Param: engineName" => $_ , Benchmark => "benchmarks.dynamic.Stacks.run" }}
      queryChoices($dbh, $table, "Param: engineName", Benchmark => "benchmarks.dynamic.Stacks.run"));

  my $query = queryDataset($dbh, query($table, "Param: work", "Benchmark", "Param: engineName"));
  plotDatasets("conflicts", "Asymmetric Workloads", {xlabel => "Work"},
    $query->("pessimistic cheap", "benchmarks.conflict.ExpensiveConflict.g:cheap", "parrp"),
    $query->("pessimistic expensive", "benchmarks.conflict.ExpensiveConflict.g:expensive", "parrp"),
    $query->("stm cheap", "benchmarks.conflict.ExpensiveConflict.g:cheap", "stm"),
    $query->("stm expensive", "benchmarks.conflict.ExpensiveConflict.g:expensive", "stm"));

  plotDatasets("conflicts", "STM aborts", {xlabel => "Work"},
    $query->("stm cheap", "benchmarks.conflict.ExpensiveConflict.g:cheap", "stm"),
    $query->("stm expensive", "benchmarks.conflict.ExpensiveConflict.g:expensive", "stm"),
    $query->("stm expensive tried", "benchmarks.conflict.ExpensiveConflict.g:tried", "stm"));

  $dbh->commit();
}

sub prettyName($name) {
  $name =~  s/spinning|REScalaSpin|parrp/ParRP/;
  $name =~  s/stm|REScalaSTM/STM/;
  $name =~  s/synchron|REScalaSync/Synchron/;
  return $name;
}

sub query($tableName, $varying, @keys) {
  my $where = join " AND ", map {qq["$_" = ?]} @keys;
  return qq[SELECT "$varying", sum(Score * Samples) / sum(Samples) FROM "$tableName" WHERE $where GROUP BY "$varying" ORDER BY "$varying"];
}

sub queryChoices($dbh, $table, $key, %constraints) {
  $constraints{1} = "1";
  my $where = join " AND ", map {qq["$_" = ?]} keys %constraints;
  return @{$dbh->selectcol_arrayref(qq[SELECT DISTINCT "$key" FROM "$table" WHERE $where], undef, values %constraints)};
}

sub plotBenchmarksFor($dbh, $tableName, $group, $name, @graphs) {
  my @datasets;
  for my $graph (@graphs) {
    my $title = delete $graph->{"Title"};
    my @keys = keys %{$graph};
    push @datasets, queryDataset($dbh, query($tableName, "Threads", @keys))->(prettyName($title) // "unnamed", values %{$graph});
  }
  plotDatasets($group, $name, {}, @datasets);
}

sub queryDataset($dbh, $query) {
  my $sth = $dbh->prepare($query);
  return sub($title, @params) {
    $sth->execute(@params);
    my $data = $sth->fetchall_arrayref();
    return makeDataset($title, $data) if (@$data);
    say "query for $title had no results: [$query] @params";
    return;
  }
}

sub coloring($name) {
  given (prettyName($name)) {
    when (/ParRP/) {  'linecolor "green"' }
    when (/STM/) {  'linecolor "blue"' }
    when (/Synchron/) {  'linecolor "red"' }
    when (/fair/) { 'linecolor "yellow"'}
    default { '' }
  }
}

sub styling($name) {
  given($name) {
    when (/(\d+)/) { "pt $1"}
    default { '' }
  }
}

sub makeDataset($title, $data) {
  $data = [sort {$a->[0] <=> $b->[0]} @$data];
  Chart::Gnuplot::DataSet->new(
    xdata => [map {$_->[0]} @$data],
    ydata => [map {$_->[1]} @$data],
    title => $title,
    style => 'linespoints ' . coloring($title) . " " . styling($title),
  );
}

sub plotDatasets($group, $name, $additionalParams, @datasets) {
  mkdir $group;
  unless (@datasets) {
    say "dataset for $group/$name is empty";
    return;
  }
  my $nospace = $name =~ s/\s//gr; # / highlighter
  my $chart = Chart::Gnuplot->new(
    output => "$group/$nospace.pdf",
    terminal => "pdf size 8,5 enhanced font 'Linux Libertine O,14'",
    key => "right top", #outside
    title  => $name,
    xlabel => "Threads",
    #logscale => "x 2; set logscale y 10",
    ylabel => "Operations Per Millisecond",
    %$additionalParams
  );
  $chart->plot2d(@datasets);
}



##### IMPORTING

sub importCSV($folder, $dbh, $tableName) {
  my @files;
  find(sub {
      push @files, $File::Find::name if $_ =~ /\.csv$/;
    }, $folder);
  for my $file (@files) {
    my @data = @{ csv(in => $file) };
    say "$file is empty" and next if !@data;
    my @headers = @{ shift @data };
    updateTable($dbh, $tableName, @headers);

    for my $row (@data) {
      s/(?<=\d),(?=\d)/./g for @$row;  # replace , with . in numbers
    }
    my $sth = $dbh->prepare("INSERT INTO $tableName (" . (join ",", map {qq["$_"]} @headers) . ") VALUES (" . (join ',', map {'?'} @headers) . ")");
    $sth->execute(@$_) for @data;
  }
  $dbh->do("UPDATE $tableName SET Score = Score / 1000, Unit = 'ops/ms' WHERE Unit = 'ops/s'");
  $dbh->commit();
  return $dbh;
}

sub updateTable($dbh, $tableName, @columns) {

  sub typeColumn($columnName) {
    given($columnName) {
      when(["Threads", "Score", 'Score Error (99,9%)', 'Samples', 'Param: depth', 'Param: sources']) { return qq["$columnName" REAL] }
      default { return qq["$columnName"] }
    }
  }

  if($dbh->selectrow_array("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'")) {
    my %knownColumns = map {$_ => 1} @{ $dbh->selectcol_arrayref("PRAGMA table_info($tableName)", { Columns => [2]}) };
    @columns = grep {! defined $knownColumns{$_} } @columns;
    $dbh->do("ALTER TABLE $tableName ADD COLUMN ". typeColumn($_) . " DEFAULT NULL") for @columns;
    return $dbh;
  }

  $dbh->do("CREATE TABLE $tableName (" . (join ',', map { typeColumn($_) . " DEFAULT NULL" } @columns) . ')')
    or die "could not create table";
  return $dbh;
}