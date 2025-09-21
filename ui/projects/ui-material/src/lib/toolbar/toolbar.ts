import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';

@Component({
  selector: 'ab-toolbar',
  standalone: true,
  imports: [CommonModule, MatToolbarModule],
  templateUrl: './toolbar.html',
  styleUrl: './toolbar.css'
})
export class Toolbar {
  @Input() color: 'primary' | 'accent' | 'warn' | undefined = undefined;
}
